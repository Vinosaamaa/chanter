package com.chanter.common.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class OrdinaryOperationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Fixture.class);

    @Test void recoveryKeepsPrivateHandlersButDoesNotCreateOrdinaryWorkers() {
        context.withPropertyValues("chanter.recovery-mode=true").run(value -> {
            assertThat(value).hasNotFailed().hasBean("reapplyHandler").doesNotHaveBean("ordinaryWorker");
        });
    }
    @Test void normalModePreservesWorkersAndInvalidSettingsFailStartup() {
        context.run(value -> assertThat(value).hasNotFailed().hasBean("ordinaryWorker"));
        context.withPropertyValues("chanter.recovery-mode=false").run(value -> assertThat(value).hasBean("ordinaryWorker"));
        context.withPropertyValues("chanter.recovery-mode=tru").run(value -> assertThat(value).hasFailed());
    }
    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean String reapplyHandler() { return "private"; }
        @Bean @OrdinaryOperation Integer ordinaryWorker() { return 1; }
    }
}
