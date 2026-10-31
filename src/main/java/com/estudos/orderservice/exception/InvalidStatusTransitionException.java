package com.estudos.orderservice.exception;

import com.estudos.orderservice.domain.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Transição de status inválida: %s -> %s. Permitidas: %s"
                .formatted(from, to, from.allowedTransitions()));
    }
}
