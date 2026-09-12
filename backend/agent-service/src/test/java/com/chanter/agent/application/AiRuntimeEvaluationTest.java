package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class AiRuntimeEvaluationTest {
    @TestFactory Stream<DynamicTest> protocolEvaluationV1() throws Exception {
        var fixture = new ObjectMapper().readTree(getClass().getResourceAsStream("/evaluations/ai-runtime-v1.json"));
        assertThat(fixture.path("version").asText()).isEqualTo("ai-runtime-v1");
        return java.util.stream.StreamSupport.stream(fixture.path("cases").spliterator(), false).map(sample ->
                DynamicTest.dynamicTest(sample.path("id").asText(), () -> {
                    var validator = new GroundedAnswerValidator();
                    var sources = List.of(new SourceCitation(UUID.randomUUID(), "Guide", sample.path("source").asText()));
                    String actual;
                    try {
                        validator.prompt("How does a queue work?", sources);
                        var answer = validator.stream(sources, () -> {}, ignored -> {});
                        answer.accept(sample.path("output").asText());
                        actual = answer.finish().confidence().name();
                    } catch (LlmProviderException refused) { actual = refused.outcome().name(); }
                    assertThat(actual).isEqualTo(sample.path("expected").asText());
                }));
    }
}
