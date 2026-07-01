package com.chanter.message.api;

import com.chanter.message.application.ChannelSummaryDigest;
import com.chanter.message.application.ChannelSummaryFollowUpsSection;
import com.chanter.message.application.ChannelSummaryTextItemsSection;
import com.chanter.message.application.ChannelSummaryTopicSection;
import java.util.List;

public record ChannelSummaryDigestResponse(
        ChannelSummaryTopicSectionResponse topTopics,
        ChannelSummaryFollowUpsSectionResponse unansweredFollowUps,
        ChannelSummaryTextItemsSectionResponse keyDecisions,
        ChannelSummaryTextItemsSectionResponse resourceLinks
) {
    public static ChannelSummaryDigestResponse from(ChannelSummaryDigest digest) {
        return new ChannelSummaryDigestResponse(
                ChannelSummaryTopicSectionResponse.from(digest.topTopics()),
                ChannelSummaryFollowUpsSectionResponse.from(digest.unansweredFollowUps()),
                ChannelSummaryTextItemsSectionResponse.from(digest.keyDecisions()),
                ChannelSummaryTextItemsSectionResponse.from(digest.resourceLinks())
        );
    }
}

record ChannelSummaryTopicSectionResponse(String title, String summary) {
    static ChannelSummaryTopicSectionResponse from(ChannelSummaryTopicSection section) {
        return new ChannelSummaryTopicSectionResponse(section.title(), section.summary());
    }
}

record ChannelSummaryFollowUpsSectionResponse(int count, String summary, List<String> questions) {
    static ChannelSummaryFollowUpsSectionResponse from(ChannelSummaryFollowUpsSection section) {
        return new ChannelSummaryFollowUpsSectionResponse(section.count(), section.summary(), section.questions());
    }
}

record ChannelSummaryTextItemsSectionResponse(String summary, List<String> items) {
    static ChannelSummaryTextItemsSectionResponse from(ChannelSummaryTextItemsSection section) {
        return new ChannelSummaryTextItemsSectionResponse(section.summary(), section.items());
    }
}
