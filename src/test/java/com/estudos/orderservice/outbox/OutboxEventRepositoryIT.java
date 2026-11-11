package com.estudos.orderservice.outbox;

import com.estudos.orderservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Queries nativas da outbox, que dependem do MySQL de verdade (FOR UPDATE SKIP LOCKED).
 */
class OutboxEventRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanOutbox() {
        // a outbox recebe eventos de outras classes de teste; começamos sempre com ela vazia
        jdbcClient.sql("DELETE FROM outbox_events").update();
    }

    @Test
    void lockPendingBatchReturnsOnlyUnpublishedInCreationOrder() {
        OutboxEvent first = repository.save(event());
        OutboxEvent published = event();
        published.markPublished();
        repository.save(published);
        OutboxEvent second = repository.save(event());

        List<Long> ids = transactionTemplate.execute(status ->
                repository.lockPendingBatch(10).stream().map(OutboxEvent::getId).toList());

        assertThat(ids).containsExactly(first.getId(), second.getId());
    }

    @Test
    void concurrentRelaysSkipRowsLockedByEachOther() throws Exception {
        List<Long> all = List.of(repository.save(event()).getId(),
                repository.save(event()).getId(),
                repository.save(event()).getId());

        // "instância A" trava os 2 primeiros e, com a transação ainda aberta, a "instância B" busca os seus
        List<Long> takenByB = transactionTemplate.execute(status -> {
            List<Long> takenByA = repository.lockPendingBatch(2).stream().map(OutboxEvent::getId).toList();
            assertThat(takenByA).containsExactly(all.get(0), all.get(1));

            return CompletableFuture.supplyAsync(() -> transactionTemplate.execute(inner ->
                    repository.lockPendingBatch(10).stream().map(OutboxEvent::getId).toList())).join();
        });

        // sem SKIP LOCKED, B ficaria bloqueada esperando A (e o join acima travaria)
        assertThat(takenByB).containsExactly(all.get(2));
    }

    @Test
    void deletePublishedBeforeRemovesOnlyOldPublishedEvents() {
        OutboxEvent oldPublished = repository.save(event());
        OutboxEvent recentPublished = repository.save(event());
        OutboxEvent pending = repository.save(event());
        Instant now = Instant.now();
        setPublishedAt(oldPublished, now.minus(10, ChronoUnit.DAYS));
        setPublishedAt(recentPublished, now.minus(1, ChronoUnit.DAYS));

        Integer deleted = transactionTemplate.execute(status ->
                repository.deletePublishedBefore(now.minus(7, ChronoUnit.DAYS)));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).extracting(OutboxEvent::getId)
                .containsExactlyInAnyOrder(recentPublished.getId(), pending.getId());
    }

    private void setPublishedAt(OutboxEvent event, Instant publishedAt) {
        jdbcClient.sql("UPDATE outbox_events SET published_at = ? WHERE id = ?")
                .params(Timestamp.from(publishedAt), event.getId())
                .update();
    }

    private static OutboxEvent event() {
        // agregado fictício: não colide com pedidos reais criados pelos outros testes
        return new OutboxEvent(UUID.randomUUID().toString(), "OutboxTest", "outbox-it", "TEST_EVENT",
                "order-events-it", "{}");
    }
}
