package com.chanter.common.recovery;

import com.chanter.common.events.OutboxMetrics;
import com.chanter.common.telemetry.PrivateErrorReporter;
import com.chanter.common.telemetry.PrivateErrorsConfiguration;
import com.chanter.common.telemetry.QueueMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryReportingIsolationTest {
    @Test void recoveryOverridesOrdinaryReportingFlagsBeforeConstructingExternalClients() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true", "chanter.telemetry.enabled=true",
                        "chanter.errors.enabled=true")
                .withUserConfiguration(OutboxMetrics.class, PrivateErrorsConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(QueueMetrics.class)
                        .doesNotHaveBean(PrivateErrorReporter.class));
    }
}
