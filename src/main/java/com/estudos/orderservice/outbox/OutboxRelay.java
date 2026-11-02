package com.estudos.orderservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lê os eventos pendentes da outbox e envia ao Kafka, em ordem de criação.
 * Entrega "pelo menos uma vez": se o envio der certo mas o commit da marcação falhar,
 * o evento é reenviado. Consumidores devem deduplicar pelo eventId (header "eventId").
 */
@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Duration retention;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate transactionTemplate,
                       @Value("${app.outbox.relay.batch-size}") int batchSize,
                       @Value("${app.outbox.relay.send-timeout}") Duration sendTimeout,
                       @Value("${app.outbox.retention}") Duration retention) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-interval-ms}")
    public void relayPending() {
        // as linhas ficam travadas (FOR UPDATE) até o fim desta transação
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> batch = repository.lockPendingBatch(batchSize);
            for (OutboxEvent event : batch) {
                if (!send(event)) {
                    // para no primeiro erro para não publicar eventos mais novos antes do que falhou
                    break;
                }
            }
        });
    }

    private boolean send(OutboxEvent event) {
        var record = new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
        record.headers()
                .add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8))
                .add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        try {
            var metadata = kafkaTemplate.send(record)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .getRecordMetadata();
            event.markPublished();
            log.info("Outbox {}: {} de {} {} publicado em {}-{}@{}", event.getId(), event.getEventType(),
                    event.getAggregateType(), event.getAggregateId(),
                    metadata.topic(), metadata.partition(), metadata.offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("interrompido");
            return false;
        } catch (Exception e) {
            event.markFailed(e.toString());
            log.warn("Outbox {}: falha ao publicar {} (tentativa {}), nova tentativa no próximo ciclo: {}",
                    event.getId(), event.getEventType(), event.getAttempts(), e.toString());
            return false;
        }
    }

    /** Remove eventos já publicados há mais tempo que a retenção, para a tabela não crescer sem limite. */
    @Scheduled(cron = "${app.outbox.cleanup-cron}")
    public void cleanupPublished() {
        Instant before = Instant.now().minus(retention);
        Integer deleted = transactionTemplate.execute(status -> repository.deletePublishedBefore(before));
        if (deleted != null && deleted > 0) {
            log.info("Outbox: {} eventos publicados antes de {} removidos", deleted, before);
        }
    }
}
