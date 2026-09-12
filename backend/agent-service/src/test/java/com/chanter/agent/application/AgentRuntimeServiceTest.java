package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.application.LlmChatClient.LlmChatResponse;
import com.chanter.agent.config.LlmProperties.Model;
import com.chanter.agent.domain.AnswerConfidence;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentRuntimeServiceTest {
    private final LlmModelCatalog catalog = mock(LlmModelCatalog.class);
    private final AiGenerationLedger ledger = mock(AiGenerationLedger.class);
    private final LlmChatClient client = mock(LlmChatClient.class);
    private final Model model = new Model("Local", "ollama", "fixture", null, null, 2048, 128, Duration.ofSeconds(3), Set.of(), null);
    private final UUID ticket = UUID.randomUUID();
    private final GroundingResult grounding = new GroundingResult("Approved excerpt", AnswerConfidence.HIGH,
            List.of(new SourceCitation(UUID.randomUUID(), "Guide", "A queue preserves first-in, first-out order.")));

    @Test void sourceOnlyPreservesGroundingWithoutProviderWorkOrReservations() {
        var runtime = new AgentRuntimeService(catalog, ledger, new GroundedAnswerValidator());
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var answer = runtime.orchestrate("How?", grounding, invocation("source-only"), execution, ignored -> {});
            assertThat(answer.result()).isEqualTo(grounding);
            assertThat(answer.llmUsed()).isFalse();
            verifyNoInteractions(ledger, client);
        }
    }

    @Test void reservesBeforeStreamingAndSettlesOnlyValidatedQuotes() {
        configure();
        doAnswer(call -> {
            verify(ledger).reserve(any(), any(), any(), eq("local"), eq(model));
            Consumer<String> chunks = call.getArgument(2);
            chunks.accept("{\"sourceId\":\"S1\",\"quote\":\"A queue preserves first-in, first-out order.\"}\n");
            return new LlmChatResponse("wire", "fixture", 20, 12);
        }).when(client).stream(any(), any(), any());
        var runtime = new AgentRuntimeService(catalog, ledger, new GroundedAnswerValidator());
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var answer = runtime.orchestrate("How does a queue work?", grounding, invocation("local"), execution, ignored -> {});
            assertThat(answer.result().answerBody()).contains("first-in, first-out");
            assertThat(answer.llmUsed()).isTrue();
            verify(ledger).settle(eq(ticket), eq(new LlmUsage(20, 12, null, null, null)), eq("SUCCESS"), anyLong(), eq("fixture"), isNull(), eq(model));
        }
    }

    @Test void providerFailureProducesAnExplicitHumanHandoffAndUnknownUsage() {
        configure();
        when(client.stream(any(), any(), any())).thenThrow(new LlmProviderException(LlmProviderException.Outcome.UNAVAILABLE));
        var runtime = new AgentRuntimeService(catalog, ledger, new GroundedAnswerValidator());
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            var answer = runtime.orchestrate("How?", grounding, invocation("local"), execution, ignored -> {});
            assertThat(answer.result().confidence()).isEqualTo(AnswerConfidence.LOW);
            assertThat(answer.result().answerBody()).contains("unavailable");
            assertThat(answer.llmUsed()).isTrue();
            verify(ledger).settle(eq(ticket), eq(LlmUsage.UNKNOWN), eq("UNAVAILABLE"), anyLong(), eq("fixture"), isNull(), eq(model));
        }
    }
    private void configure() {
        when(catalog.definition("local")).thenReturn(model);
        when(catalog.client("local")).thenReturn(client);
        when(ledger.reserve(any(), any(), any(), anyString(), any())).thenReturn(ticket);
    }

    @Test void authorizationRevokedBeforeTransportSettlesKnownZeroWithoutCallingTheProvider() {
        configure();
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var invocation = new AgentRuntimeService.Invocation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "local", () -> {
            if (checks.incrementAndGet() == 2) throw new IllegalStateException("revoked");
        });
        var runtime = new AgentRuntimeService(catalog, ledger, new GroundedAnswerValidator());
        try (var execution = new LlmExecution(Duration.ofSeconds(3))) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.orchestrate("How?", grounding, invocation, execution, ignored -> {}))
                    .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(client);
        verify(ledger).settle(eq(ticket), eq(new LlmUsage(0, 0, 0, 0, 0)), eq("REJECTED_EVIDENCE"), anyLong(), eq("fixture"), isNull(), eq(model));
    }

    @Test void configuredProviderDeadlineInterruptsAStalledGenerationBeforeTheOuterRequestDeadline() {
        configure();
        Model bounded = new Model("Local", "ollama", "fixture", null, null, 2048, 128, Duration.ofSeconds(1), Set.of(), null);
        when(catalog.definition("local")).thenReturn(bounded);
        doAnswer(call -> {
            LlmExecution providerExecution = call.getArgument(1);
            var interrupted = new java.util.concurrent.CountDownLatch(1);
            try (var hook = providerExecution.onCancel(interrupted::countDown)) {
                assertThat(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                providerExecution.check();
            }
            throw new AssertionError("A stalled provider must be cancelled");
        }).when(client).stream(any(), any(), any());
        var runtime = new AgentRuntimeService(catalog, ledger, new GroundedAnswerValidator());
        try (var execution = new LlmExecution(Duration.ofSeconds(10))) {
            var answer = runtime.orchestrate("How?", grounding, invocation("local"), execution, ignored -> {});
            assertThat(answer.result().answerBody()).contains("did not finish in time");
            execution.check();
            verify(ledger).settle(eq(ticket), eq(LlmUsage.UNKNOWN), eq("TIMED_OUT"), anyLong(), eq("fixture"), isNull(), eq(bounded));
        }
    }
    private AgentRuntimeService.Invocation invocation(String selection) {
        return new AgentRuntimeService.Invocation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), selection, () -> {});
    }
}
