package com.estudos.orderservice.messaging;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado no tópico de pedidos. A chave da mensagem é o id do pedido,
 * então todos os eventos de um mesmo pedido caem na mesma partição (ordem garantida).
 */
public record OrderEvent(
        String eventId,
        OrderEventType eventType,
        Long orderId,
        Long customerId,
        OrderStatus status,
        OrderStatus previousStatus,
        BigDecimal totalAmount,
        Instant occurredAt
) {
    public static OrderEvent created(Order order) {
        return of(OrderEventType.ORDER_CREATED, order, null);
    }

    public static OrderEvent statusChanged(Order order, OrderStatus previousStatus) {
        return of(OrderEventType.ORDER_STATUS_CHANGED, order, previousStatus);
    }

    private static OrderEvent of(OrderEventType type, Order order, OrderStatus previousStatus) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                type,
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                previousStatus,
                order.getTotalAmount(),
                Instant.now());
    }
}
