package com.estudos.orderservice.web;

import com.estudos.orderservice.domain.Order;
import com.estudos.orderservice.domain.OrderStatus;
import com.estudos.orderservice.exception.InvalidStatusTransitionException;
import com.estudos.orderservice.exception.OrderNotFoundException;
import com.estudos.orderservice.service.OrderService;
import com.estudos.orderservice.web.dto.CreateOrderRequest;
import com.estudos.orderservice.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(orderService.create(any(CreateOrderRequest.class))).thenReturn(sampleResponse(OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 1, "items": [{"productId": 10, "quantity": 2, "unitPrice": 15.50}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createReturns400WhenItemsAreMissing() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 1, "items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void findByIdReturns404WhenNotFound() throws Exception {
        when(orderService.findById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pedido não encontrado"));
    }

    @Test
    void updateStatusReturns422OnInvalidTransition() throws Exception {
        when(orderService.updateStatus(eq(1L), eq(OrderStatus.DELIVERED)))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.PENDING, OrderStatus.DELIVERED));

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void updateStatusReturns409OnConcurrentModification() throws Exception {
        when(orderService.updateStatus(eq(1L), eq(OrderStatus.CONFIRMED)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito de concorrência"));
    }

    private static OrderResponse sampleResponse(OrderStatus status) {
        return new OrderResponse(1L, 1L, status, new BigDecimal("31.00"), List.of(),
                Instant.now(), Instant.now());
    }
}
