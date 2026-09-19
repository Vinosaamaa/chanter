package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TurnstileVerificationTest {
    @Test void actualVerificationRequiresSuccessfulSingleUseTokenForExpectedHostnameAndAction() throws Exception {
        var payload = new AtomicReference<>("{\"success\":true,\"hostname\":\"study.example\",\"action\":\"register\"}");
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> {
            calls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("secret=fixture-secret", "response=fixture-token");
            byte[] response = payload.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var verifier = new TurnstileVerification("fixture-site", "fixture-secret", "https://study.example", true,
                    HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/verify"));
            assertThat(verifier.verify("fixture-token", "register")).isEqualTo(TurnstileVerification.Result.PASSED);
            payload.set("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}");
            assertThat(verifier.verify("fixture-token", "register")).isEqualTo(TurnstileVerification.Result.REJECTED);
            payload.set("{\"success\":true,\"hostname\":\"another.example\",\"action\":\"register\"}");
            assertThat(verifier.verify("fixture-token", "register")).isEqualTo(TurnstileVerification.Result.REJECTED);
            payload.set("{\"success\":true,\"hostname\":\"study.example\",\"action\":\"recovery\"}");
            assertThat(verifier.verify("fixture-token", "register")).isEqualTo(TurnstileVerification.Result.REJECTED);
            assertThat(verifier.verify("x".repeat(2049), "register")).isEqualTo(TurnstileVerification.Result.REJECTED);
            assertThat(calls).hasValue(4);
            server.stop(0);
            assertThat(verifier.verify("fixture-token", "register")).isEqualTo(TurnstileVerification.Result.UNAVAILABLE);
        } finally { server.stop(0); }
    }

    @Test void emailFallbackRequiresEnforcedEmailVerificationAndCompleteConfiguration() {
        assertThatThrownBy(() -> new TurnstileVerification("fixture", "", "https://study.example", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TurnstileVerification("fixture", "fixture-secret", "https://study.example", false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TurnstileVerification("", "", "http://localhost:5173", false).options().enabled()).isFalse();
    }
}
