package com.chanter.message.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateChannelSummaryRequest(
        @Min(1) @Max(90) Integer windowDays
) {
    public int resolvedWindowDays() {
        return windowDays == null ? 7 : windowDays;
    }
}
