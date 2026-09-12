package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiAnswerModeTest {
    @Test void selectsFreeSourcesAndExplicitExtractionWithoutPretendingSynthesisIsAvailable() {
        assertThat(AiAnswerMode.resolve(null, "source-only")).isEqualTo("source-only");
        assertThat(AiAnswerMode.resolve(null, "local")).isEqualTo("quoted-evidence");
        assertThat(AiAnswerMode.resolve("source-only", "local")).isEqualTo("source-only");
        assertThatThrownBy(() -> AiAnswerMode.resolve("grounded-explanation", "local"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> AiAnswerMode.resolve("quoted-evidence", "source-only"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }
}
