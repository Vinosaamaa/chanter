package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailDeliveryWorkerTest {

    @Test
    void configuredScheduleDrainsAndMaintainsTheDurableQueue() {
        var outbox = mock(JdbcEmailOutbox.class);
        new ApplicationContextRunner()
                .withUserConfiguration(EmailDeliveryWorker.class)
                .withBean(JdbcEmailOutbox.class, () -> outbox)
                .withPropertyValues("chanter.email.poll-interval=25ms")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailDeliveryWorker.class);
                    verify(outbox, timeout(3000).atLeastOnce()).purgeCompletedMetadata();
                    verify(outbox, atLeastOnce()).deliverNext();
                });
    }
}
