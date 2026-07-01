package com.chanter.message.application;

import java.util.List;

public record ChannelSummaryFollowUpsSection(
        int count,
        String summary,
        List<String> questions
) {
}
