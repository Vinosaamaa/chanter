package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import com.chanter.media.infra.S3PrivateResourceStorage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class StorageNamespaceTest {
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final HttpServer SERVER = server();
    private static final String ENDPOINT = "http://127.0.0.1:" + SERVER.getAddress().getPort();
    @Autowired ResourceLifecycle lifecycle;
    @Autowired S3PrivateResourceStorage configured;
    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> { REQUESTS.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
            server.start(); return server;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:storage-namespace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("chanter.media.storage-backend", () -> "s3");
        registry.add("chanter.media.s3.endpoint", () -> ENDPOINT);
        registry.add("chanter.media.s3.region", () -> "us-east-1");
        registry.add("chanter.media.s3.bucket", () -> "original-private-bucket");
        registry.add("chanter.media.s3.access-key", () -> "fixture-access");
        registry.add("chanter.media.s3.secret-key", () -> "fixture-secret");
        registry.add("chanter.media.s3.allow-local-http", () -> true);
    }
    @AfterAll static void stop() { SERVER.stop(0); }
    @Test void changedBucketOrEndpointCannotStartOrMakeAnyObjectRequest() {
        for (String[] changed : new String[][] {{ENDPOINT, "different-private-bucket"}, {ENDPOINT.replace("127.0.0.1", "localhost"), "original-private-bucket"}}) {
            assertThatThrownBy(() -> new S3PrivateResourceStorage(lifecycle, changed[0], "us-east-1", changed[1], "fixture-access", "fixture-secret", true))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("namespace");
        }
        assertThat(REQUESTS.get()).isZero();
    }
    @Test void credentialRotationAndNormalizedEndpointKeepTheSameBinding() {
        var rotated = new S3PrivateResourceStorage(lifecycle, ENDPOINT + "/", "us-east-1", "original-private-bucket", "rotated-access", "rotated-secret", true);
        rotated.close(); assertThat(REQUESTS.get()).isZero();
    }
}
