package com.chanter.community.domain;

import java.util.List;

public record HomeSummary(
        List<HomeSummaryCourse> courses,
        List<HomeSummaryAttentionItem> attention,
        List<HomeSummaryUpNextItem> upNext,
        List<String> partialFailures
) {
}
