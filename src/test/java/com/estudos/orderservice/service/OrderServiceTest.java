package com.estudos.orderservice.service;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderItem;
import com.estudos.orderservice.domain.OrderStatus;
import com.estudos.orderservice.exception.InvalidStatusTransitionException;
import com.estudos.orderservice.exception.OrderNotFoundException;
import com.estudos.orderservice.messaging.OrderEvent;
import com.estudos.orderservice.messaging.OrderEventOutbox;
import com.estudos.orderservice.messaging.OrderEventType;
import com.estudos.orderservice.repository.OrderRepository;
import com.estudos.orderservice.web.dto.CreateOrderRequest;
import com.estudos.orderservice.web.dto.OrderItemRequest;
import com.estudos.orderservice.web.dto.OrderResponse;
import com.estudos.orderservice.web.dto.OrderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventOutbox orderEventOutbox;

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

        OrderEvent event = captureEvent();
        assertThat(event.eventType()).isEqualTo(OrderEventType.ORDER_CREATED);
        assertThat(event.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.previousStatus()).isNull();
        assertThat(event.totalAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void findAllWithoutStatusListsEveryOrder() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(newOrder()), pageable, 1));

        Page<OrderSummaryResponse> page = orderService.findAll(null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).findByStatus(any(), any());
    }

    @Test
    void findAllWithStatusFiltersByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByStatus(OrderStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(newOrder()), pageable, 1));

        Page<OrderSummaryResponse> page = orderService.findAll(OrderStatus.PENDING, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findByIdReturnsOrderWithItems() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(newOrder()));

        OrderResponse response = orderService.findById(1L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(1);
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

        OrderEvent event = captureEvent();
        assertThat(event.eventType()).isEqualTo(OrderEventType.ORDER_STATUS_CHANGED);
        assertThat(event.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(newOrder()));

        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(orderEventOutbox, never()).record(any());
    }

    private OrderEvent captureEvent() {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventOutbox).record(captor.capture());
        return captor.getValue();
    }

    private static Order newOrder() {
        Order order = new Order(1L);
        order.addItem(new OrderItem(10L, 1, new BigDecimal("10.00")));
        return order;
    }
}
