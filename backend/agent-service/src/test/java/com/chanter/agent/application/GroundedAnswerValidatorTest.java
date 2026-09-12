package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GroundedAnswerValidatorTest {
    private final SourceCitation source = new SourceCitation(UUID.randomUUID(), "Guide", "A queue preserves first-in, first-out order. A stack is different.");

    @Test void streamsOnlyExactSupportedQuotesWithServerOwnedCitations() {
        var chunks = new ArrayList<String>();
        var checks = new AtomicInteger();
        var stream = new GroundedAnswerValidator().stream(List.of(source), checks::incrementAndGet, chunks::add);
        stream.accept("{\"sourceId\":\"S1\",\"quote\":\"A queue preserves ");
        assertThat(chunks).isEmpty();
        stream.accept("first-in, first-out order.\"}\n");
        assertThat(chunks).hasSize(1);
        var answer = stream.finish();
        assertThat(answer.citations()).extracting(SourceCitation::resourceId).containsExactly(source.resourceId());
        assertThat(answer.answerBody()).contains("A queue preserves first-in, first-out order.");
        assertThat(checks.get()).isGreaterThanOrEqualTo(1);
    }

    @Test void rejectsInventedCitationsAndParaphrasesBeforePublishing() {
        var chunks = new ArrayList<String>();
        var validator = new GroundedAnswerValidator();
        assertThatThrownBy(() -> validator.stream(List.of(source), () -> {}, chunks::add)
                .accept("{\"sourceId\":\"S9\",\"quote\":\"A queue preserves first-in, first-out order.\"}\n"))
                .isInstanceOf(LlmProviderException.class);
        assertThatThrownBy(() -> validator.stream(List.of(source), () -> {}, chunks::add)
                .accept("{\"sourceId\":\"S1\",\"quote\":\"Queues always reverse their contents.\"}\n"))
                .isInstanceOf(LlmProviderException.class);
        assertThat(chunks).isEmpty();
    }

    @Test void rejectsRevokedEvidenceBeforeTheNextPublishedQuote() {
        var chunks = new ArrayList<String>();
        var stream = new GroundedAnswerValidator().stream(List.of(source), () -> { throw new IllegalStateException("revoked"); }, chunks::add);
        assertThatThrownBy(() -> stream.accept("{\"sourceId\":\"S1\",\"quote\":\"A queue preserves first-in, first-out order.\"}\n"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(chunks).isEmpty();
    }

    @Test void blocksCredentialBearingInputBeforeAnyProviderCanReceiveIt() {
        assertThatThrownBy(() -> new GroundedAnswerValidator().prompt("Use api_key=fixture-secret-value", List.of(source)))
                .isInstanceOf(LlmProviderException.class);
    }
}
