package com.chanter.agent.config;

import com.chanter.agent.application.EmbeddingRebuildWorker;
import com.chanter.agent.application.ResourceIngestionWorker;
import com.chanter.agent.infra.NativeRequestExpiryWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryWorkerIsolationTest {
    @Test void recoveryDoesNotConstructIngestionRebuildOrNativeExpiryWorkers() {
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true")
                .withUserConfiguration(EmbeddingRebuildWorker.class, ResourceIngestionWorker.class, NativeRequestExpiryWorker.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmbeddingRebuildWorker.class)
                        .doesNotHaveBean(ResourceIngestionWorker.class).doesNotHaveBean(NativeRequestExpiryWorker.class));
    }
}
