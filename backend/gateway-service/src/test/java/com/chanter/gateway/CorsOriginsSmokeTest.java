package com.chanter.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CorsOriginsSmokeTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("chanter.jwt.secret", () -> "chanter-test-jwt-secret-32bytes-min!!");
        registry.add(
                "chanter.cors.allowed-origins",
                () -> "http://localhost:5173,https://staging.chanter.example");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void preflightAllowsConfiguredOrigin() {
        webTestClient
                .options()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "https://staging.chanter.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-Chanter-CSRF")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://staging.chanter.example")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        value -> org.assertj.core.api.Assertions.assertThat(value).containsIgnoringCase("X-Chanter-CSRF"));
    }

    @Test
    void preflightAllowsLocalDefaultStyleOrigin() {
        webTestClient
                .options()
                .uri("/actuator/health")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void preflightRejectsUnknownOrigin() {
        webTestClient
                .options()
                .uri("/actuator/health")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectHeader()
                .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
