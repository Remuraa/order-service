package com.estudos.orderservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os jobs agendados (relay e limpeza da outbox). Desligue com app.outbox.relay.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
