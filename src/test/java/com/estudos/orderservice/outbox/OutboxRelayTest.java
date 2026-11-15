package com.estudos.orderservice.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, new TransactionTemplate(transactionManager),
                100, Duration.ofSeconds(1), Duration.ofDays(7));
    }

    @Test
    void publishesPendingEventsWithKeyAndHeadersAndMarksThemPublished() {
        OutboxEvent first = event("e-1", "1");
        OutboxEvent second = event("e-2", "2");
        when(repository.lockPendingBatch(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(success(), success());

        relay.relayPending();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getAllValues().getFirst();
        assertThat(sent.topic()).isEqualTo("order-events");
        assertThat(sent.key()).isEqualTo("1");
        assertThat(sent.value()).isEqualTo("{\"orderId\":1}");
        assertThat(new String(sent.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8)).isEqualTo("e-1");
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    void stopsBatchOnFailureToKeepOrderAndRecordsError() {
        OutboxEvent first = event("e-1", "1");
        OutboxEvent second = event("e-2", "1");
        when(repository.lockPendingBatch(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker fora")));

        relay.relayPending();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker fora");
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void interruptedSendRestoresInterruptFlagAndStopsBatch() throws Exception {
        OutboxEvent first = event("e-1", "1");
        OutboxEvent second = event("e-2", "1");
        when(repository.lockPendingBatch(100)).thenReturn(List.of(first, second));
        CompletableFuture<SendResult<String, String>> interrupted = new CompletableFuture<>() {
            @Override
            public SendResult<String, String> get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("parando");
            }
        };
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(interrupted);

        try {
            relay.relayPending();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // limpa a flag para não afetar os próximos testes na mesma thread
            Thread.interrupted();
        }
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).isEqualTo("interrompido");
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void cleanupDeletesEventsPublishedBeforeRetention() {
        when(repository.deletePublishedBefore(any(Instant.class))).thenReturn(3);
        Instant expected = Instant.now().minus(Duration.ofDays(7));

        relay.cleanupPublished();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deletePublishedBefore(captor.capture());
        assertThat(captor.getValue()).isCloseTo(expected, within(5, ChronoUnit.SECONDS));
    }

    @Test
    void cleanupWithNothingToDeleteDoesNotFail() {
        when(repository.deletePublishedBefore(any(Instant.class))).thenReturn(0);

        relay.cleanupPublished();

        verify(repository).deletePublishedBefore(any(Instant.class));
    }

    private static OutboxEvent event(String eventId, String orderId) {
        return new OutboxEvent(eventId, "Order", orderId, "ORDER_CREATED", "order-events",
                "{\"orderId\":" + orderId + "}");
    }

    private static CompletableFuture<SendResult<String, String>> success() {
        var metadata = new RecordMetadata(new TopicPartition("order-events", 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }
}
