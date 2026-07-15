package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.application.LlmChatClient.LlmChatResponse;
import com.chanter.agent.domain.AnswerConfidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentRuntimeServiceTest {

    @Test
    void disabledLlmReturnsGroundingUnchanged() {
        LlmChatClient client = mock(LlmChatClient.class);
        when(client.isEnabled()).thenReturn(false);
        when(client.providerId()).thenReturn("disabled");
        when(client.modelId()).thenReturn("none");

        AgentRuntimeService runtime = new AgentRuntimeService(client);
        GroundingResult grounding = highGrounding();
        var result = runtime.orchestrate("How?", grounding);
        assertThat(result.llmUsed()).isFalse();
        assertThat(result.result()).isEqualTo(grounding);
    }

    @Test
    void enabledLlmRefinesAnswerAndKeepsCitations() {
        LlmChatClient client = mock(LlmChatClient.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.providerId()).thenReturn("ollama");
        when(client.modelId()).thenReturn("llama3.2");
        when(client.complete(any())).thenReturn(new LlmChatResponse("Refined answer.", "llama3.2", 10, 4));

        AgentRuntimeService runtime = new AgentRuntimeService(client);
        GroundingResult grounding = highGrounding();
        var result = runtime.orchestrate("How?", grounding);
        assertThat(result.llmUsed()).isTrue();
        assertThat(result.result().answerBody()).contains("Refined answer.");
        assertThat(result.result().citations()).hasSize(1);
    }

    private static GroundingResult highGrounding() {
        return new GroundingResult(
                "Based on guide: excerpt",
                AnswerConfidence.HIGH,
                List.of(new SourceCitation(UUID.randomUUID(), "Guide", "excerpt"))
        );
    }
}
