package com.chanter.message.application;

import com.chanter.message.domain.ApprovedFaq;
import com.chanter.message.domain.SupportQuestion;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApprovedFaqService {

    private final ApprovedFaqRepository approvedFaqRepository;
    private final SupportQuestionRepository supportQuestionRepository;
    private final CourseChannelAccessClient courseChannelAccessClient;
    private final CourseResourceAccessClient courseResourceAccessClient;
    private final FaqCandidateGrouper faqCandidateGrouper;
    private final Clock clock;

    public ApprovedFaqService(
            ApprovedFaqRepository approvedFaqRepository,
            SupportQuestionRepository supportQuestionRepository,
            CourseChannelAccessClient courseChannelAccessClient,
            CourseResourceAccessClient courseResourceAccessClient,
            FaqCandidateGrouper faqCandidateGrouper,
            Clock clock
    ) {
        this.approvedFaqRepository = approvedFaqRepository;
        this.supportQuestionRepository = supportQuestionRepository;
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.courseResourceAccessClient = courseResourceAccessClient;
        this.faqCandidateGrouper = faqCandidateGrouper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FaqCandidateGroup> listFaqCandidates(UUID channelId, UUID viewerUserId) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Instructors can view FAQ candidates");
        }

        List<SupportQuestion> supportQuestions = supportQuestionRepository.findByChannelId(channelId);
        return faqCandidateGrouper.group(supportQuestions);
    }

    public ApprovedFaq createOrUpdateApprovedFaq(
            UUID courseId,
            UUID channelId,
            UUID approvedByUserId,
            UUID approvedFaqId,
            String question,
            String answer,
            List<UUID> sourceSupportQuestionIds
    ) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, approvedByUserId);
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Instructors can approve FAQs");
        }
        if (!access.courseId().equals(courseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Channel does not belong to the requested Course");
        }

        String normalizedQuestion = question.trim();
        String normalizedAnswer = answer.trim();
        if (normalizedQuestion.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved FAQ question must not be blank");
        }
        if (normalizedAnswer.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved FAQ answer must not be blank");
        }

        List<UUID> normalizedSourceIds = normalizeSourceSupportQuestionIds(sourceSupportQuestionIds);
        validateSourceSupportQuestions(channelId, normalizedSourceIds);

        if (approvedFaqId == null) {
            ApprovedFaq approvedFaq = new ApprovedFaq(
                    UUID.randomUUID(),
                    courseId,
                    normalizedQuestion,
                    normalizedAnswer,
                    approvedByUserId,
                    clock.instant(),
                    clock.instant()
            );
            return approvedFaqRepository.save(approvedFaq, normalizedSourceIds);
        }

        ApprovedFaq existing = approvedFaqRepository.findByIdAndCourseId(approvedFaqId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved FAQ not found"));

        ApprovedFaq updated = new ApprovedFaq(
                existing.id(),
                existing.courseId(),
                normalizedQuestion,
                normalizedAnswer,
                approvedByUserId,
                existing.createdAt(),
                clock.instant()
        );
        return approvedFaqRepository.update(updated, normalizedSourceIds);
    }

    @Transactional(readOnly = true)
    public List<ApprovedFaq> listApprovedFaqs(UUID courseId, UUID viewerUserId) {
        requireCourseViewAccess(courseId, viewerUserId);
        return approvedFaqRepository.findByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public List<ApprovedFaq> searchApprovedFaqs(UUID courseId, UUID viewerUserId, String query) {
        requireCourseViewAccess(courseId, viewerUserId);

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not be blank");
        }

        return approvedFaqRepository.searchByCourseId(courseId, normalizedQuery);
    }

    private void requireCourseViewAccess(UUID courseId, UUID viewerUserId) {
        CourseResourceAccess access = courseResourceAccessClient.requireAccess(courseId, viewerUserId);
        if (!access.canViewCourseResources()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Approved FAQ access requires Cohort Enrollment or Instructor role"
            );
        }
    }

    private static List<UUID> normalizeSourceSupportQuestionIds(List<UUID> sourceSupportQuestionIds) {
        if (sourceSupportQuestionIds == null || sourceSupportQuestionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved FAQ requires at least one source Support Question");
        }

        Set<UUID> uniqueIds = new HashSet<>(sourceSupportQuestionIds);
        if (uniqueIds.size() != sourceSupportQuestionIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source Support Question ids must be unique");
        }

        return List.copyOf(uniqueIds);
    }

    private void validateSourceSupportQuestions(UUID channelId, List<UUID> sourceSupportQuestionIds) {
        for (UUID supportQuestionId : sourceSupportQuestionIds) {
            supportQuestionRepository.findByIdAndChannelId(channelId, supportQuestionId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Source Support Question not found in Course Channel"
                    ));
        }
    }
}
