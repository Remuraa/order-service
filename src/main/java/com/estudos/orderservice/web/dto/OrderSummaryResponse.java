package com.estudos.orderservice.web.dto;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
