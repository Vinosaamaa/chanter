package com.chanter.search.application;

import java.util.List;
import java.util.UUID;

public interface MessageFaqClient {

    List<ApprovedFaqSummary> listApprovedFaqs(UUID courseId, UUID viewerUserId);

    record ApprovedFaqSummary(UUID id, UUID courseId, String question, String answer) {
    }
}
