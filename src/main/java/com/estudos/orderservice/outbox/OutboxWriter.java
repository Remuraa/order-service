package com.estudos.orderservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava o evento na tabela outbox_events. MANDATORY: tem que ser chamado dentro da
 * transação de negócio, senão lança exceção. É isso que garante "pedido gravado
 * se e somente se o evento foi gravado".
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String aggregateType, Object aggregateId,
                       String eventId, String eventType, Object payload) {
        String json = jsonMapper.writeValueAsString(payload);
        repository.save(new OutboxEvent(eventId, aggregateType, String.valueOf(aggregateId),
                eventType, topic, json));
    }
}
