package com.chanter.realtime.config;

import com.chanter.realtime.websocket.SocialRealtimeHub;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "chanter.telemetry.enabled", havingValue = "true")
public class RealtimeMetrics {
    @Bean MeterBinder realtimeConnections(SocialRealtimeHub hub) {
        return registry -> Gauge.builder("chanter.realtime.connections", hub, SocialRealtimeHub::connectionCount)
                .baseUnit("{connection}").register(registry);
    }
}
