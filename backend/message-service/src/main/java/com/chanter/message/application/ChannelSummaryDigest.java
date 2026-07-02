package com.chanter.message.application;

import java.util.List;

public record ChannelSummaryDigest(
        ChannelSummaryTopicSection topTopics,
        ChannelSummaryFollowUpsSection unansweredFollowUps,
        ChannelSummaryTextItemsSection keyDecisions,
        ChannelSummaryTextItemsSection resourceLinks
) {
}
