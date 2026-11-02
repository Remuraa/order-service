-- Transactional Outbox: o evento é gravado na mesma transação do pedido
-- e depois enviado ao Kafka pelo OutboxRelay.
CREATE TABLE outbox_events
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id       CHAR(36)     NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        JSON         NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000) NULL,
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id),
    -- busca dos pendentes (published_at IS NULL) em ordem de criação
    INDEX idx_outbox_events_pending (published_at, id)
) ENGINE = InnoDB;
