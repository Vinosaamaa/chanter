package com.chanter.auth;

import com.chanter.auth.infra.EmailDeliveryWorker;
import com.chanter.auth.infra.EmailQueueMetrics;
import com.chanter.common.telemetry.QueueMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryWorkerIsolationTest {
    @Test void recoveryDoesNotConstructEmailDeliveryOrMetadataExpiryWorker() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true").withUserConfiguration(EmailDeliveryWorker.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmailDeliveryWorker.class));
    }
    @Test void recoveryDisablesEmailSamplingEvenWithRestoredTelemetryEnabled() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true", "chanter.telemetry.enabled=true")
                .withUserConfiguration(EmailQueueMetrics.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(QueueMetrics.class));
    }
}
