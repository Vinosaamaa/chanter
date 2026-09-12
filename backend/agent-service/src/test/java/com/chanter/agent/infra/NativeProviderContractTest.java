package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.assertThat;
import com.chanter.agent.application.LlmChatClient.LlmChatRequest;
import com.chanter.agent.application.LlmExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.chanter.agent.application.LlmProviderException;
import org.junit.jupiter.api.Test;

class NativeProviderContractTest {
    @Test void ollamaRejectsOversizedVisibleOutputEvenWhenReportedUsageIsSmall() throws Exception {
        String text = "é".repeat(33);
        String fixture = new ObjectMapper().writeValueAsString(java.util.Map.of("message", java.util.Map.of("content", text),
                "done", true, "done_reason", "stop", "eval_count", 1)) + "\n";
        var server = fixtureServer("/api/chat", fixture);
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new OllamaLlmChatClient("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-local", Duration.ofSeconds(3));
            var chunks = new ArrayList<String>();
            assertThatThrownBy(() -> client.stream(new LlmChatRequest("s", "u", 16), execution, chunks::add))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("limit_exceeded");
            assertThat(chunks).isEmpty();
            assertThatThrownBy(() -> client.complete(new LlmChatRequest("s", "u", 16), execution))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("limit_exceeded");
        } finally { server.stop(0); }
    }

    @Test void ollamaInBandErrorIsUnavailableWithoutLeakingItsDiagnostic() throws Exception {
        var server = fixtureServer("/api/chat", "{\"error\":\"private diagnostic fixture\"}\n");
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new OllamaLlmChatClient("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-local", Duration.ofSeconds(3));
            assertThatThrownBy(() -> client.stream(new LlmChatRequest("s", "u"), execution, ignored -> {}))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("unavailable").hasMessageNotContaining("private diagnostic");
        } finally { server.stop(0); }
    }
    @Test void anthropicRejectsTextAfterItsTerminalEvent() throws Exception {
        String fixture = "data: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-fixture\"}}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"First\"}}\n\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Invalid late text\"}}\n\n";
        var server = fixtureServer("/v1/messages", fixture);
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new AnthropicLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "claude-fixture");
            var chunks = new ArrayList<String>();
            assertThatThrownBy(() -> client.stream(new LlmChatRequest("s", "u"), execution, chunks::add))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("invalid_response");
            assertThat(chunks).containsExactly("First");
        } finally { server.stop(0); }
    }

    @Test void rateLimitFailureHasNoHiddenRetryAndDoesNotExposeTheResponseBody() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            calls.incrementAndGet();
            byte[] body = "private diagnostic fixture".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new AnthropicLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "claude-fixture");
            assertThatThrownBy(() -> client.stream(new LlmChatRequest("s", "u"), execution, ignored -> {}))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("rate_limited").hasMessageNotContaining("private diagnostic").hasNoCause();
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }
    @Test
    void localOllamaStreamsMeasuredCountsWithoutAnExternalCredential() throws Exception {
        String fixture = "{\"model\":\"fixture-local\",\"message\":{\"content\":\"Local \"},\"done\":false}\n"
                + "{\"model\":\"fixture-local\",\"message\":{\"content\":\"answer\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":7,\"eval_count\":2}\n";
        HttpServer server = fixtureServer("/api/chat", fixture);
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new OllamaLlmChatClient("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-local", Duration.ofSeconds(3));
            var chunks = new ArrayList<String>();
            var result = client.stream(new LlmChatRequest("s", "u"), execution, chunks::add);
            assertThat(chunks).containsExactly("Local ", "answer");
            assertThat(result.usage().inputTokens()).isEqualTo(7);
            assertThat(result.usage().outputTokens()).isEqualTo(2);
            assertThat(result.usage().cacheReadTokens()).isNull();
        } finally { server.stop(0); }
    }
    @Test
    void anthropicStreamsTextAndMergesCumulativeUsage() throws Exception {
        String fixture = "data: {\"type\":\"message_start\",\"message\":{\"id\":\"fixture\",\"model\":\"claude-fixture\",\"usage\":{\"input_tokens\":4,\"output_tokens\":0,\"cache_read_input_tokens\":2}}}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"First\"}}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" second\"}}\n\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        HttpServer server = fixtureServer("/v1/messages", fixture);
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new AnthropicLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "claude-fixture");
            var chunks = new ArrayList<String>();
            var result = client.stream(new LlmChatRequest("s", "u"), execution, chunks::add);
            assertThat(chunks).containsExactly("First", " second");
            assertThat(result.usage().inputTokens()).isEqualTo(6);
            assertThat(result.usage().outputTokens()).isEqualTo(3);
        } finally { server.stop(0); }
    }

    private static HttpServer fixtureServer(String path, String fixture) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = fixture.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        return server;
    }
    @Test
    void compatibleProtocolStreamsBeforeCompletionAndRetainsFinalMeasuredUsage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = ("data: {\"id\":\"request-fixture\",\"choices\":[{\"delta\":{\"content\":\"First \"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"second\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":3,\"prompt_tokens_details\":{\"cached_tokens\":2}}}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new OpenAiProtocolLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "grok-fixture", "compatible");
            var chunks = new ArrayList<String>();
            var result = client.stream(new LlmChatRequest("s", "u"), execution, chunks::add);
            assertThat(chunks).containsExactly("First ", "second");
            assertThat(result.usage().inputTokens()).isEqualTo(8);
            assertThat(result.usage().cacheReadTokens()).isEqualTo(2);
            assertThat(result.usage().outputTokens()).isEqualTo(3);
        } finally { server.stop(0); }
    }

    @Test
    void deadlineAbortsAResponseThatStallsAfterHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: ".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        long start = System.nanoTime();
        try (LlmExecution execution = new LlmExecution(Duration.ofMillis(200))) {
            var client = new OpenAiProtocolLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "grok-fixture", "compatible");
            assertThatThrownBy(() -> client.stream(new LlmChatRequest("s", "u"), execution, ignored -> {}))
                    .isInstanceOf(LlmProviderException.class).hasMessageContaining("timed_out");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally { release.countDown(); server.stop(0); }
    }
    @Test
    void anthropicUsesNativeMessagesAndCountsCacheTokensWithoutInventingMissingUsage() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> version = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            version.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            byte[] bytes = ("{\"id\":\"fixture-request\",\"model\":\"claude-fixture\",\"content\":[{\"type\":\"text\",\"text\":\"Answer\"}],"
                    + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":2,\"cache_read_input_tokens\":4,\"cache_creation_input_tokens\":5}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new AnthropicLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture-key", "claude-fixture");
            var response = client.complete(new LlmChatRequest("instructions", "question", 64), execution);
            assertThat(key.get()).isEqualTo("fixture-key");
            assertThat(version.get()).isEqualTo("2023-06-01");
            var json = new ObjectMapper().readTree(body.get());
            assertThat(json.path("system").asText()).isEqualTo("instructions");
            assertThat(json.path("max_tokens").asInt()).isEqualTo(64);
            assertThat(response.content()).isEqualTo("Answer");
            assertThat(response.usage().inputTokens()).isEqualTo(12);
            assertThat(response.usage().cacheReadTokens()).isEqualTo(4);
            assertThat(response.usage().cacheWriteTokens()).isEqualTo(5);
            assertThat(response.usage().reasoningTokens()).isNull();
        } finally { server.stop(0); }
    }

    @Test
    void compatibleProtocolUsesExplicitIdentityWithoutInventingUsage() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "{\"model\":\"grok-fixture\",\"choices\":[{\"message\":{\"content\":\"Answer\"},\"finish_reason\":\"stop\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (LlmExecution execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new OpenAiProtocolLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture-key", "grok-fixture", "compatible");
            var response = client.complete(new LlmChatRequest("s", "u"), execution);
            assertThat(client.providerId()).isEqualTo("compatible");
            assertThat(authorization.get()).isEqualTo("Bearer fixture-key");
            assertThat(response.usage().inputTokens()).isNull();
            assertThat(response.usage().outputTokens()).isNull();
        } finally { server.stop(0); }
    }
}
