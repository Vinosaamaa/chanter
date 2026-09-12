package com.chanter.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chanter.email")
public record EmailDeliveryProperties(String provider, String from, boolean localSink, Smtp smtp) {

    @Override
    public String toString() {
        return "EmailDeliveryProperties[redacted]";
    }

    public record Smtp(String host, int port, String username, String password, String tlsMode) {
        @Override
        public String toString() {
            return "Smtp[redacted]";
        }
    }
}
