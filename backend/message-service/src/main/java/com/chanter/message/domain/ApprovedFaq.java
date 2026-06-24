package com.chanter.message.domain;

import java.time.Instant;
import java.util.UUID;

public record ApprovedFaq(
        UUID id,
        UUID courseId,
        String question,
        String answer,
        UUID approvedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
