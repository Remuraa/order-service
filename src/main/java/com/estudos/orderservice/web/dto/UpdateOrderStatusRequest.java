package com.estudos.orderservice.web.dto;

import com.estudos.orderservice.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
