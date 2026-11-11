package com.estudos.orderservice.web;

import com.estudos.orderservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo HTTP completo: controller -> service -> JPA/Flyway no MySQL real. */
@AutoConfigureMockMvc
class OrderApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createPersistsOrderWithItemsAndOutboxEvent() throws Exception {
        long id = createOrder(7L);

        Map<String, Object> order = jdbcClient.sql("SELECT customer_id, status, total_amount FROM orders WHERE id = ?")
                .param(id).query().singleRow();
        assertThat(order.get("customer_id")).isEqualTo(7L);
        assertThat(order.get("status")).isEqualTo("PENDING");
        assertThat((BigDecimal) order.get("total_amount")).isEqualByComparingTo("40.00");

        Integer items = jdbcClient.sql("SELECT COUNT(*) FROM order_items WHERE order_id = ?")
                .param(id).query(Integer.class).single();
        assertThat(items).isEqualTo(2);

        // gravado na mesma transação do pedido (pode já ter sido publicado pelo relay, por isso não olhamos published_at)
        List<String> eventTypes = jdbcClient.sql("""
                        SELECT event_type FROM outbox_events
                        WHERE aggregate_type = 'Order' AND aggregate_id = ? ORDER BY id""")
                .param(String.valueOf(id)).query(String.class).list();
        assertThat(eventTypes).containsExactly("ORDER_CREATED");
    }

    @Test
    void findByIdReturnsOrderWithItems() throws Exception {
        long id = createOrder(8L);

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerId").value(8))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(40.00))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void findByIdReturns404WhenOrderDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pedido não encontrado"));
    }

    @Test
    void updateStatusPersistsTransitionAndRecordsEvent() throws Exception {
        long id = createOrder(9L);

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Map<String, Object> order = jdbcClient.sql("SELECT status, version FROM orders WHERE id = ?")
                .param(id).query().singleRow();
        assertThat(order.get("status")).isEqualTo("CONFIRMED");
        assertThat(order.get("version")).isEqualTo(1L);

        List<String> eventTypes = jdbcClient.sql("""
                        SELECT event_type FROM outbox_events
                        WHERE aggregate_type = 'Order' AND aggregate_id = ? ORDER BY id""")
                .param(String.valueOf(id)).query(String.class).list();
        assertThat(eventTypes).containsExactly("ORDER_CREATED", "ORDER_STATUS_CHANGED");
    }

    @Test
    void invalidTransitionReturns422AndChangesNothing() throws Exception {
        long id = createOrder(10L);

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isUnprocessableContent());

        String status = jdbcClient.sql("SELECT status FROM orders WHERE id = ?")
                .param(id).query(String.class).single();
        assertThat(status).isEqualTo("PENDING");

        Integer events = jdbcClient.sql("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?")
                .param(String.valueOf(id)).query(Integer.class).single();
        assertThat(events).isEqualTo(1);
    }

    @Test
    void findAllFiltersByStatus() throws Exception {
        long cancelled = createOrder(11L);
        long pending = createOrder(11L);
        mockMvc.perform(patch("/api/v1/orders/{id}/status", cancelled)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/orders").param("status", "CANCELLED").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = jsonMapper.readTree(result.getResponse().getContentAsString()).get("content");
        List<Long> ids = content.valueStream().map(order -> order.get("id").asLong()).toList();
        assertThat(ids).contains(cancelled).doesNotContain(pending);
        assertThat(content.valueStream().map(order -> order.get("status").asString()))
                .containsOnly("CANCELLED");
    }

    private long createOrder(long customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "items": [
                                  {"productId": 10, "quantity": 2, "unitPrice": 15.50},
                                  {"productId": 20, "quantity": 1, "unitPrice": 9.00}
                                ]}
                                """.formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
