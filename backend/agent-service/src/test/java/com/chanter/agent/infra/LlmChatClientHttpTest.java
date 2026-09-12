package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.assertThat;
import com.chanter.agent.application.LlmChatClient.LlmChatRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmChatClientHttpTest {
    @Test void ollamaCompletionBoundsOutputAndMapsMeasuredUsage() throws Exception {
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"model\":\"fixture-local\",\"message\":{\"content\":\"Hello\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":7,\"eval_count\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new OllamaLlmChatClient("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-local", 2, 3);
            var response = client.complete(new LlmChatRequest("sys", "user", 32));
            assertThat(LlmHttpTransport.JSON.readTree(body.get()).path("options").path("num_predict").asInt()).isEqualTo(32);
            assertThat(response.content()).isEqualTo("Hello");
            assertThat(response.usage().inputTokens()).isEqualTo(7);
            assertThat(response.usage().outputTokens()).isEqualTo(2);
        } finally { server.stop(0); }
    }

    @Test void openAiUsesResponsesWithExplicitIdentityAndUnknownMissingUsage() throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"model\":\"gpt-fixture\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello\"}]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new ResponsesLlmChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "fixture", "gpt-fixture", "openai");
            var response = client.complete(new LlmChatRequest("s", "u"));
            assertThat(authorization.get()).isEqualTo("Bearer fixture");
            assertThat(client.providerId()).isEqualTo("openai");
            assertThat(response.content()).isEqualTo("Hello");
            assertThat(response.usage().measured()).isFalse();
            assertThat(response.usage().inputTokens()).isNull();
        } finally { server.stop(0); }
    }
}
