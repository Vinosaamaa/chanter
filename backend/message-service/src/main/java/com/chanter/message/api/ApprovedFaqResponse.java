package com.chanter.message.api;

import com.chanter.message.domain.ApprovedFaq;
import java.time.Instant;
import java.util.UUID;

public record ApprovedFaqResponse(
        UUID id,
        UUID courseId,
        String question,
        String answer,
        UUID approvedByUserId,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApprovedFaqResponse from(ApprovedFaq approvedFaq) {
        return new ApprovedFaqResponse(
                approvedFaq.id(),
                approvedFaq.courseId(),
                approvedFaq.question(),
                approvedFaq.answer(),
                approvedFaq.approvedByUserId(),
                approvedFaq.createdAt(),
                approvedFaq.updatedAt()
        );
    }
}
