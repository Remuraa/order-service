package com.estudos.orderservice.service;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderItem;
import com.estudos.orderservice.domain.OrderStatus;
import com.estudos.orderservice.exception.OrderNotFoundException;
import com.estudos.orderservice.messaging.OrderEvent;
import com.estudos.orderservice.messaging.OrderEventOutbox;
import com.estudos.orderservice.repository.OrderRepository;
import com.estudos.orderservice.web.dto.CreateOrderRequest;
import com.estudos.orderservice.web.dto.OrderResponse;
import com.estudos.orderservice.web.dto.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventOutbox orderEventOutbox;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = new Order(request.customerId());
        request.items().forEach(item ->
                order.addItem(new OrderItem(item.productId(), item.quantity(), item.unitPrice())));

        Order saved = orderRepository.save(order);
        log.info("Pedido {} criado para o cliente {} (total {})",
                saved.getId(), saved.getCustomerId(), saved.getTotalAmount());
        // gravado na outbox na mesma transação; o OutboxRelay envia ao Kafka depois
        orderEventOutbox.record(OrderEvent.created(saved));
        return OrderResponse.from(saved);
    }

    public Page<OrderSummaryResponse> findAll(OrderStatus status, Pageable pageable) {
        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return orders.map(OrderSummaryResponse::from);
    }

    public OrderResponse findById(Long id) {
        return OrderResponse.from(getOrder(id));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = getOrder(id);
        OrderStatus previous = order.getStatus();
        order.changeStatus(newStatus);
        // flush para que @PreUpdate e @Version sejam aplicados antes de montar a resposta
        orderRepository.flush();
        log.info("Pedido {} alterado de {} para {}", id, previous, newStatus);
        orderEventOutbox.record(OrderEvent.statusChanged(order, previous));
        return OrderResponse.from(order);
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
