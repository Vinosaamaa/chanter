package com.chanter.message.application;

import java.util.List;

public record ChannelSummaryTextItemsSection(
        String summary,
        List<String> items
) {
    public ChannelSummaryTextItemsSection {
        items = List.copyOf(items);
    }
}
