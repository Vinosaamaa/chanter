package com.chanter.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "chanter.email.worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);
    private final JdbcEmailOutbox outbox;

    public EmailDeliveryWorker(JdbcEmailOutbox outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${chanter.email.poll-interval:5s}", initialDelayString = "${chanter.email.poll-interval:5s}")
    public void deliverPending() {
        try {
            for (int sent = 0; sent < 20 && outbox.deliverNext(); sent++) {
                // Each message owns one transaction; other workers skip the row while SMTP is in progress.
            }
            outbox.purgeCompletedMetadata();
        } catch (RuntimeException exception) {
            // SQL or provider exception details may contain message contents or connection configuration.
            log.error("Auth email worker failed; pending messages will be retried");
        }
    }
}
