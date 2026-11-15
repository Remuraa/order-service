package com.estudos.orderservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void enabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void enabledWhenPropertyIsTrue() {
        contextRunner.withPropertyValues("app.outbox.relay.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void disabledWhenPropertyIsFalse() {
        contextRunner.withPropertyValues("app.outbox.relay.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SchedulingConfig.class));
    }
}
