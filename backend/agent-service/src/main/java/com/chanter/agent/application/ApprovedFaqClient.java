package com.chanter.agent.application;

import java.util.List;
import java.util.UUID;

public interface ApprovedFaqClient {

    List<ApprovedFaqSummary> listApprovedFaqs(UUID courseId, UUID viewerUserId);

    List<ApprovedFaqSummary> searchApprovedFaqs(UUID courseId, UUID viewerUserId, String query);

    record ApprovedFaqSummary(UUID id, UUID courseId, String question, String answer) {
    }
}
