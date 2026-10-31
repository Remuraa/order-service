package com.estudos.orderservice.web.dto;

import com.estudos.orderservice.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(),
                item.getUnitPrice(), item.getSubtotal());
    }
}
