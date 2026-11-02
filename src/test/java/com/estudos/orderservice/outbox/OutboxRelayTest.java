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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static OutboxEvent event(String eventId, String orderId) {
        return new OutboxEvent(eventId, "Order", orderId, "ORDER_CREATED", "order-events",
                "{\"orderId\":" + orderId + "}");
    }

    private static CompletableFuture<SendResult<String, String>> success() {
        var metadata = new RecordMetadata(new TopicPartition("order-events", 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }
}
