package com.estudos.orderservice.service;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderItem;
import com.estudos.orderservice.domain.OrderStatus;
import com.estudos.orderservice.exception.InvalidStatusTransitionException;
import com.estudos.orderservice.exception.OrderNotFoundException;
import com.estudos.orderservice.repository.OrderRepository;
import com.estudos.orderservice.web.dto.CreateOrderRequest;
import com.estudos.orderservice.web.dto.OrderItemRequest;
import com.estudos.orderservice.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createCalculatesTotalAndStartsAsPending() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateOrderRequest(1L, List.of(
                new OrderItemRequest(10L, 2, new BigDecimal("15.50")),
                new OrderItemRequest(20L, 1, new BigDecimal("9.00"))));

        OrderResponse response = orderService.create(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("40.00");
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void findByIdThrowsWhenOrderDoesNotExist() {
        when(orderRepository.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatusAppliesValidTransition() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(newOrder()));

        OrderResponse response = orderService.updateStatus(1L, OrderStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(newOrder()));

        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static Order newOrder() {
        Order order = new Order(1L);
        order.addItem(new OrderItem(10L, 1, new BigDecimal("10.00")));
        return order;
    }
}
