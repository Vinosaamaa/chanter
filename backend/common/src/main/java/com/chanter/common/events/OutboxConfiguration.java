package com.chanter.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@Import(OutboxOperations.class)
public class OutboxConfiguration {
    // Keep Spring's unqualified lifecycle jobs off the network-delivery scheduler.
    @Bean
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("source-jobs-");
        return scheduler;
    }
    @Bean
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler durableEventScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("durable-events-");
        return scheduler;
    }
    @Bean NotificationEventWriter notificationEventWriter(DurableOutbox outbox, ObjectMapper mapper) {
        return new NotificationEventWriter(outbox, mapper);
    }
    @Bean SearchEventWriter searchEventWriter(DurableOutbox outbox, ObjectMapper mapper) {
        return new SearchEventWriter(outbox, mapper);
    }
    @Bean ResourceEventWriter resourceEventWriter(DurableOutbox outbox, ObjectMapper mapper) {
        return new ResourceEventWriter(outbox, mapper);
    }
    @Bean DurableOutbox durableOutbox(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            @Value("${spring.application.name}") String serviceName) {
        return new DurableOutbox(jdbc, new TransactionTemplate(transactions), serviceName.replace("-service", ""), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name="chanter.events.dispatch-enabled", havingValue="true", matchIfMissing=true)
    DispatchSchedule eventDispatchSchedule(DurableOutbox outbox, ObjectMapper mapper,
            @Value("${SEARCH_SERVICE_URL:http://localhost:8088}") String search,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8089}") String notification,
            @Value("${AGENT_SERVICE_URL:http://localhost:8085}") String agent,
            @Value("${chanter.internal-service-token}") String token) {
        return new DispatchSchedule(new OutboxDispatcher(outbox, mapper, Map.of(
                "search", URI.create(search + "/api/v1/internal/events"),
                "agent", URI.create(agent + "/api/v1/internal/events"),
                "notification", URI.create(notification + "/api/v1/internal/events")), token));
    }

    static final class DispatchSchedule {
        private final OutboxDispatcher dispatcher;
        DispatchSchedule(OutboxDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @Scheduled(fixedDelayString="${chanter.events.poll-delay-ms:1000}", scheduler="durableEventScheduler")
        public void poll() { dispatcher.drain(); }
    }
}
