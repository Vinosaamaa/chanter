package com.chanter.message.application;

import com.chanter.message.domain.ApprovedFaq;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovedFaqRepository {

    ApprovedFaq save(ApprovedFaq approvedFaq, List<UUID> sourceSupportQuestionIds);

    ApprovedFaq update(ApprovedFaq approvedFaq, List<UUID> sourceSupportQuestionIds);

    Optional<ApprovedFaq> findByIdAndCourseId(UUID approvedFaqId, UUID courseId);

    List<ApprovedFaq> findByCourseId(UUID courseId);

    List<ApprovedFaq> searchByCourseId(UUID courseId, String query);
}
