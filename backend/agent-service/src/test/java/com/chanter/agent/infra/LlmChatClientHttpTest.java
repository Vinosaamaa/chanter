package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chanter.agent.application.LlmChatClient.LlmChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class LlmChatClientHttpTest {

    @Test
    void ollamaCompleteMapsChatResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ollama.test/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"message\":{\"content\":\"Hello from Ollama\"}}",
                        MediaType.APPLICATION_JSON
                ));

        OllamaLlmChatClient client = new OllamaLlmChatClient(builder.build(), "llama3.2");
        var response = client.complete(new LlmChatRequest("sys", "user"));
        assertThat(response.content()).isEqualTo("Hello from Ollama");
        assertThat(response.model()).isEqualTo("llama3.2");
        server.verify();
    }

    @Test
    void openaiCompleteRequiresApiKeyAndMapsUsage() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://openai.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://openai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1}}",
                        MediaType.APPLICATION_JSON
                ));

        OpenAiCompatibleLlmChatClient client = new OpenAiCompatibleLlmChatClient(builder.build(), "test-key", "gpt-4o-mini");
        var response = client.complete(new LlmChatRequest("sys", "user"));
        assertThat(response.content()).isEqualTo("Hi");
        assertThat(response.promptTokens()).isEqualTo(3);
        assertThat(response.completionTokens()).isEqualTo(1);
        server.verify();
    }

    @Test
    void openaiWithoutKeyFailsSafely() {
        OpenAiCompatibleLlmChatClient client = new OpenAiCompatibleLlmChatClient(
                RestClient.builder().baseUrl("http://openai.test/v1").build(),
                "",
                "gpt-4o-mini"
        );
        assertThatThrownBy(() -> client.complete(new LlmChatRequest("s", "u")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
