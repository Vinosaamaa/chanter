package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.chanter.media.infra.S3PrivateResourceStorage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class S3AdapterPolicyTest {
    @TempDir Path directory;
    @Test void serverFailuresMakeExactlyOneNetworkAttemptAndConsumeTheCorrectBudget() throws Exception {
        var counter = new AtomicInteger(); var lifecycle = mock(ResourceLifecycle.class);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            counter.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] error = "<Error><Code>InternalError</Code><Message>private-provider-detail</Message></Error>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml"); exchange.sendResponseHeaders(500, error.length);
            try (var response = exchange.getResponseBody()) { response.write(error); }
        }); server.start();
        var adapter = new S3PrivateResourceStorage(lifecycle, "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "fixture-bucket", "fixture-key", "fixture-secret", true);
        try {
            String key = PrivateResourceStorage.PREFIX + UUID.randomUUID() + "/" + UUID.randomUUID() + "/" + UUID.randomUUID();
            Path content = directory.resolve("fixture.txt"); Files.writeString(content, "fixture");
            assertThatThrownBy(() -> adapter.put(key, content, UploadValidator.checksum(content)))
                    .isInstanceOf(java.io.IOException.class).hasMessage("Private object storage is unavailable");
            assertThatThrownBy(() -> adapter.open(key)).isInstanceOf(java.io.IOException.class);
            assertThatThrownBy(() -> adapter.list(null)).isInstanceOf(java.io.IOException.class);
            assertThatThrownBy(() -> adapter.delete(key)).isInstanceOf(java.io.IOException.class);
            assertThat(counter.get()).isEqualTo(4);
            verify(lifecycle, times(3)).countRequest(false); verify(lifecycle).countRequest(true);
            doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                    .when(lifecycle).countRequest(false);
            assertThatThrownBy(() -> adapter.open(key)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(counter.get()).isEqualTo(4);
        } finally { adapter.close(); server.stop(0); }
    }
    @Test void insecureOrCredentialBearingProviderUrlsAreRejectedBeforeNetworkUse() {
        var lifecycle = mock(ResourceLifecycle.class);
        for (String endpoint : java.util.List.of("http://example.com", "https://user:secret@example.com", "https://example.com/?key=secret")) {
            assertThatThrownBy(() -> new S3PrivateResourceStorage(lifecycle, endpoint, "region", "fixture-bucket", "key", "secret", true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid private S3 configuration");
        }
    }
}
