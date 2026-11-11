package com.estudos.orderservice.outbox;

import com.estudos.orderservice.domain.OrderStatus;
import com.estudos.orderservice.service.OrderService;
import com.estudos.orderservice.support.AbstractIntegrationTest;
import com.estudos.orderservice.web.dto.CreateOrderRequest;
import com.estudos.orderservice.web.dto.OrderItemRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Ponta a ponta da outbox: pedido gravado no MySQL -> OutboxRelay -> mensagem no Kafka.
 * O agendamento está desligado nos testes, então o relay é acionado aqui explicitamente.
 */
class OutboxKafkaIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.kafka.topics.order-events}")
    private String topic;

    private Consumer<String, String> consumer;

    /** Tudo que o consumidor já leu; o tópico é compartilhado entre os testes, então filtramos pela chave. */
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(topic));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void publishesOrderCreatedWithKeyHeadersAndPayload() {
        long orderId = orderService.create(new CreateOrderRequest(42L, List.of(
                new OrderItemRequest(10L, 2, new BigDecimal("15.00"))))).id();

        outboxRelay.relayPending();
        List<ConsumerRecord<String, String>> records = awaitRecords(orderId, 1);

        ConsumerRecord<String, String> record = records.getFirst();
        assertThat(record.key()).isEqualTo(String.valueOf(orderId));
        assertThat(header(record, "eventType")).isEqualTo("ORDER_CREATED");

        JsonNode payload = jsonMapper.readTree(record.value());
        assertThat(payload.get("eventId").asString()).isEqualTo(header(record, "eventId"));
        assertThat(payload.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(payload.get("customerId").asLong()).isEqualTo(42L);
        assertThat(payload.get("status").asString()).isEqualTo("PENDING");
        // a outbox guarda o JSON como texto justamente para não perder a escala (15.00 -> 30.00, não 30.0)
        assertThat(record.value()).contains("\"totalAmount\":30.00");

        // e a linha da outbox fica marcada como publicada
        Map<String, Object> row = jdbcClient.sql("""
                        SELECT published_at, attempts, last_error FROM outbox_events WHERE event_id = ?""")
                .param(header(record, "eventId")).query().singleRow();
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isNull();
    }

    @Test
    void eventStaysPendingUntilRelayRuns() {
        long orderId = orderService.create(new CreateOrderRequest(44L, List.of(
                new OrderItemRequest(10L, 1, new BigDecimal("10.00"))))).id();

        assertThat(publishedAt(orderId)).isNull();
        assertThat(consumer.poll(Duration.ofSeconds(2)))
                .noneMatch(r -> String.valueOf(orderId).equals(r.key()));

        outboxRelay.relayPending();

        awaitRecords(orderId, 1);
        assertThat(publishedAt(orderId)).isNotNull();
    }

    @Test
    void eventsOfSameOrderArriveInOrderOnSamePartition() {
        long orderId = orderService.create(new CreateOrderRequest(43L, List.of(
                new OrderItemRequest(10L, 1, new BigDecimal("10.00"))))).id();
        orderService.updateStatus(orderId, OrderStatus.CONFIRMED);
        orderService.updateStatus(orderId, OrderStatus.SHIPPED);
        orderService.updateStatus(orderId, OrderStatus.DELIVERED);

        outboxRelay.relayPending();
        List<ConsumerRecord<String, String>> records = awaitRecords(orderId, 4);

        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records).extracting(r -> jsonMapper.readTree(r.value()).get("status").asString())
                .containsExactly("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED");
        assertThat(records).extracting(r -> header(r, "eventType"))
                .containsExactly("ORDER_CREATED", "ORDER_STATUS_CHANGED", "ORDER_STATUS_CHANGED", "ORDER_STATUS_CHANGED");
    }

    private List<ConsumerRecord<String, String>> awaitRecords(long orderId, int expected) {
        String key = String.valueOf(orderId);
        await().atMost(TIMEOUT).until(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            return received.stream().filter(r -> key.equals(r.key())).count() >= expected;
        });
        return received.stream().filter(r -> key.equals(r.key())).toList();
    }

    private Object publishedAt(long orderId) {
        return jdbcClient.sql("SELECT published_at FROM outbox_events WHERE aggregate_id = ?")
                .param(String.valueOf(orderId)).query().singleRow().get("published_at");
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
