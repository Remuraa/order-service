package com.estudos.orderservice.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void startsPendingWithoutAttempts() {
        OutboxEvent event = newEvent();

        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void markFailedKeepsShortErrorAsIs() {
        OutboxEvent event = newEvent();

        event.markFailed("broker fora");

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker fora");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void markFailedAcceptsNullError() {
        OutboxEvent event = newEvent();

        event.markFailed(null);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void markFailedTruncatesLongErrorToColumnSize() {
        OutboxEvent event = newEvent();

        event.markFailed("x".repeat(1500));

        assertThat(event.getLastError()).hasSize(1000);
    }

    @Test
    void markFailedKeepsErrorWithExactlyMaxLength() {
        OutboxEvent event = newEvent();
        String error = "x".repeat(1000);

        event.markFailed(error);

        assertThat(event.getLastError()).isEqualTo(error);
    }

    @Test
    void markPublishedAfterFailureClearsErrorAndCountsAttempt() {
        OutboxEvent event = newEvent();
        event.markFailed("broker fora");

        event.markPublished();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).isNull();
    }

    private static OutboxEvent newEvent() {
        return new OutboxEvent("e-1", "Order", "1", "ORDER_CREATED", "order-events", "{\"orderId\":1}");
    }
}
