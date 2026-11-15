package com.estudos.orderservice.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static com.estudos.orderservice.domain.OrderStatus.CANCELLED;
import static com.estudos.orderservice.domain.OrderStatus.CONFIRMED;
import static com.estudos.orderservice.domain.OrderStatus.DELIVERED;
import static com.estudos.orderservice.domain.OrderStatus.PENDING;
import static com.estudos.orderservice.domain.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(PENDING, Set.of(CONFIRMED, CANCELLED)),
                Arguments.of(CONFIRMED, Set.of(SHIPPED, CANCELLED)),
                Arguments.of(SHIPPED, Set.of(DELIVERED)),
                Arguments.of(DELIVERED, Set.of()),
                Arguments.of(CANCELLED, Set.of()));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void allowsOnlyExpectedTransitions(OrderStatus from, Set<OrderStatus> allowed) {
        assertThat(from.allowedTransitions()).containsExactlyInAnyOrderElementsOf(allowed);

        for (OrderStatus target : OrderStatus.values()) {
            assertThat(from.canTransitionTo(target))
                    .as("%s -> %s", from, target)
                    .isEqualTo(allowed.contains(target));
        }
    }
}
