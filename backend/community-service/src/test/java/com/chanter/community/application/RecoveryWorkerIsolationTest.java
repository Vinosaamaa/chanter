package com.chanter.community.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryWorkerIsolationTest {
    @Test void recoveryDoesNotConstructLiveMediaReconciliation() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true")
                .withUserConfiguration(LiveMediaReconciler.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LiveMediaReconciler.class));
    }
}
