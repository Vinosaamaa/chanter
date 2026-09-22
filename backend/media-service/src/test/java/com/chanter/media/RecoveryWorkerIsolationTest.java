package com.chanter.media;

import com.chanter.media.application.ResourceWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryWorkerIsolationTest {
    @Test void recoveryDoesNotConstructScanningIndexingOrObjectReconciliationWorker() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true").withUserConfiguration(ResourceWorker.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResourceWorker.class));
    }
}
