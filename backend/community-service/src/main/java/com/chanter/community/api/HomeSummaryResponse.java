package com.chanter.community.api;

import com.chanter.community.domain.HomeSummary;
import com.chanter.community.domain.HomeSummaryAttentionItem;
import com.chanter.community.domain.HomeSummaryCourse;
import com.chanter.community.domain.HomeSummaryUpNextItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeSummaryResponse(
        List<CourseItem> courses,
        List<AttentionItem> attention,
        List<UpNextItem> upNext,
        List<String> partialFailures
) {
    public static HomeSummaryResponse from(HomeSummary summary) {
        return new HomeSummaryResponse(
                summary.courses().stream().map(CourseItem::from).toList(),
                summary.attention().stream().map(AttentionItem::from).toList(),
                summary.upNext().stream().map(UpNextItem::from).toList(),
                summary.partialFailures()
        );
    }

    public record CourseItem(
            UUID courseId,
            UUID studyServerId,
            String title,
            UUID cohortId,
            String cohortName,
            String instructorDisplayName,
            Integer progress,
            String progressUnavailableReason,
            String href
    ) {
        static CourseItem from(HomeSummaryCourse course) {
            return new CourseItem(
                    course.courseId(),
                    course.studyServerId(),
                    course.title(),
                    course.cohortId(),
                    course.cohortName(),
                    course.instructorDisplayName(),
                    course.progress(),
                    course.progressUnavailableReason(),
                    course.href()
            );
        }
    }

    public record AttentionItem(
            String id,
            String kind,
            String headline,
            String suffix,
            boolean suffixOnNewLine,
            String actionLabel,
            String actionVariant,
            String href,
            Instant startsAt
    ) {
        static AttentionItem from(HomeSummaryAttentionItem item) {
            return new AttentionItem(
                    item.id(),
                    item.kind(),
                    item.headline(),
                    item.suffix(),
                    item.suffixOnNewLine(),
                    item.actionLabel(),
                    item.actionVariant(),
                    item.href(),
                    item.startsAt()
            );
        }
    }

    public record UpNextItem(
            String id,
            String kind,
            String title,
            String suffix,
            String detail,
            String actionLabel,
            String href,
            Instant startsAt
    ) {
        static UpNextItem from(HomeSummaryUpNextItem item) {
            return new UpNextItem(
                    item.id(),
                    item.kind(),
                    item.title(),
                    item.suffix(),
                    item.detail(),
                    item.actionLabel(),
                    item.href(),
                    item.startsAt()
            );
        }
    }
}
