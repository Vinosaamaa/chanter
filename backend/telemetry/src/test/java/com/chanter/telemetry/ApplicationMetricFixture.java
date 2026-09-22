package com.chanter.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import com.chanter.common.events.DurableOutbox;
import com.chanter.common.events.OutboxMetrics;
import com.chanter.common.telemetry.QueueMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses Boot's actual registry, as the email and gateway application counters do. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(OutboxMetrics.class)
public class ApplicationMetricFixture {
    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(ApplicationMetricFixture.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        try (var context = application.run("--chanter.telemetry.enabled=true")) {
            if (context.getBeansOfType(QueueMetrics.class).size() != 1) throw new IllegalStateException("Enabled monitoring did not start its source collector");
            var metrics = context.getBean(MeterRegistry.class);
            metrics.counter("chanter.ai.settlements", "outcome", "TIMED_OUT", "usage", "unmeasured").increment(2);
            metrics.counter("chanter.ai.unmeasured_settlements").increment(2);
            metrics.timer("chanter.ai.duration", "outcome", "TIMED_OUT").record(20, java.util.concurrent.TimeUnit.MILLISECONDS);
            for (int index = 0; index < 600; index++) {
                metrics.counter("chanter.auth.email.delivery", "outcome", "accepted", "account", "private-canary-" + index).increment();
            }
            metrics.counter("chanter.auth.email.delivery", "outcome", "retry").increment(3);
            metrics.counter("chanter.gateway.admission", "operation", "AI", "outcome", "limited").increment(2);
            metrics.counter("private-canary", "account", "private-canary").increment();
            // Keep gauge registration alive until an actual receiver acknowledges
            // the row and business counter. Final shutdown export alone loses gauges.
            Path acknowledged = Path.of(System.getenv("CHANTER_FIXTURE_ACK"));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(acknowledged) && System.nanoTime() < deadline) Thread.sleep(20);
            if (!Files.exists(acknowledged)) throw new IllegalStateException("Queue export was not acknowledged");
        }
    }
    @Bean DataSource source() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source); jdbc.execute(DurableOutbox.SCHEMA);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var outbox = new DurableOutbox(jdbc, transactions, "message", Clock.systemUTC());
        transactions.executeWithoutResult(ignored -> outbox.append("notification", "NOTIFICATION_REQUESTED", "private-canary", "{}"));
        return source;
    }
}
