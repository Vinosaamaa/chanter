package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.assertThat;
import com.chanter.agent.application.LlmChatClient.LlmChatRequest;
import com.chanter.agent.application.LlmExecution;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResponsesProviderContractTest {
    @Test
    void xaiResponsesBoundsAllOutputAndRecordsReasoningInclusiveUsage() throws Exception {
        var sent = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = ("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}\n\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"fixture\",\"model\":\"grok-fixture\",\"status\":\"completed\","
                    + "\"usage\":{\"input_tokens\":32,\"output_tokens\":103,\"input_tokens_details\":{\"cached_tokens\":6},\"output_tokens_details\":{\"reasoning_tokens\":94}}}}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var client = new XaiLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "grok-fixture");
            var chunks = new ArrayList<String>();
            var result = client.stream(new LlmChatRequest("s", "u", 128), execution, chunks::add);
            var request = LlmHttpTransport.JSON.readTree(sent.get());
            assertThat(request.path("max_output_tokens").asInt()).isEqualTo(128);
            assertThat(request.path("store").asBoolean(true)).isFalse();
            assertThat(request.path("tools")).isEmpty();
            assertThat(client.providerId()).isEqualTo("xai");
            assertThat(chunks).containsExactly("Answer");
            assertThat(result.usage().inputTokens()).isEqualTo(32);
            assertThat(result.usage().outputTokens()).isEqualTo(103);
            assertThat(result.usage().reasoningTokens()).isEqualTo(94);
        } finally { server.stop(0); }
    }
}
