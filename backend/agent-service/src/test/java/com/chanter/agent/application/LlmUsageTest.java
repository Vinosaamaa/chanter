package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class LlmUsageTest {
    @Test void cacheAndReasoningDetailsCannotExceedTheirInclusiveTotals() {
        assertThatThrownBy(() -> new LlmUsage(10, 5, 7, 7, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmUsage(10, 5, null, null, 6)).isInstanceOf(IllegalArgumentException.class);
    }
}
