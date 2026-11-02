-- A coluna JSON do MySQL normaliza o conteúdo (reordena chaves e converte 15.00 em 15.0).
-- A outbox precisa publicar exatamente o JSON que foi gerado, então guardamos como texto.
ALTER TABLE outbox_events MODIFY payload MEDIUMTEXT NOT NULL;
