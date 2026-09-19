package com.chanter.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/** Uses Boot's actual registry, as the email and gateway application counters do. */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ApplicationMetricFixture {
    public static void main(String[] args) {
        var application = new SpringApplication(ApplicationMetricFixture.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        try (var context = application.run()) {
            var metrics = context.getBean(MeterRegistry.class);
            for (int index = 0; index < 600; index++) {
                metrics.counter("chanter.auth.email.delivery", "outcome", "accepted", "account", "private-canary-" + index).increment();
            }
            metrics.counter("chanter.auth.email.delivery", "outcome", "retry").increment(3);
            metrics.counter("chanter.gateway.admission", "operation", "AI", "outcome", "limited").increment(2);
            metrics.counter("private-canary", "account", "private-canary").increment();
        }
    }
}
