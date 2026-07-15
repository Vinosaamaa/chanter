package com.chanter.community.api;

import com.chanter.community.domain.CourseOverviewItem;
import com.chanter.community.domain.CourseOverviewSummary;
import java.time.Instant;
import java.util.List;

public record CourseOverviewSummaryResponse(
        Integer progress,
        String progressUnavailableReason,
        List<Item> thisWeek,
        List<Item> recentActivity,
        List<Item> upNext,
        List<String> partialFailures
) {
    public static CourseOverviewSummaryResponse from(CourseOverviewSummary summary) {
        return new CourseOverviewSummaryResponse(
                summary.progress(),
                summary.progressUnavailableReason(),
                summary.thisWeek().stream().map(Item::from).toList(),
                summary.recentActivity().stream().map(Item::from).toList(),
                summary.upNext().stream().map(Item::from).toList(),
                summary.partialFailures()
        );
    }

    public record Item(
            String id,
            String kind,
            String title,
            String detail,
            String actionLabel,
            String href,
            Instant startsAt
    ) {
        static Item from(CourseOverviewItem item) {
            return new Item(
                    item.id(),
                    item.kind(),
                    item.title(),
                    item.detail(),
                    item.actionLabel(),
                    item.href(),
                    item.startsAt()
            );
        }
    }
}
