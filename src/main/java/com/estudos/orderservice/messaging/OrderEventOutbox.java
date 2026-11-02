package com.estudos.orderservice.messaging;

import com.estudos.orderservice.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Registra eventos de pedido na outbox, já com o tópico e a chave (id do pedido). */
@Component
public class OrderEventOutbox {

    private static final String AGGREGATE_TYPE = "Order";

    private final OutboxWriter outboxWriter;
    private final String topic;

    public OrderEventOutbox(OutboxWriter outboxWriter,
                            @Value("${app.kafka.topics.order-events}") String topic) {
        this.outboxWriter = outboxWriter;
        this.topic = topic;
    }

    public void record(OrderEvent event) {
        outboxWriter.append(topic, AGGREGATE_TYPE, event.orderId(),
                event.eventId(), event.eventType().name(), event);
    }
}
