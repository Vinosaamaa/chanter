package com.chanter.agent.application;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.GroundingSource;
import com.chanter.agent.application.StudyAssistantService.Presence;
import com.chanter.agent.application.SupportQuestionChannelAccessClient.SupportQuestionChannelAccess;
import com.chanter.agent.application.SupportQuestionClient.SupportQuestion;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroundedSupportQuestionService {

    private final StudyAssistantService studyAssistantService;
    private final SupportQuestionChannelAccessClient channelAccessClient;
    private final SupportQuestionClient supportQuestionClient;
    private final CourseResourceCatalogClient courseResourceCatalogClient;
    private final CourseResourceContentClient courseResourceContentClient;
    private final ApprovedFaqClient approvedFaqClient;
    private final GroundingEngine groundingEngine;
    private final AiQuotaEnforcementService aiQuotaEnforcementService;
    private final StudyAssistantAnswerPersistenceService answerPersistenceService;
    private final StudyAssistantAnswerRepository answerRepository;
    private final Clock clock;

    public GroundedSupportQuestionService(
            StudyAssistantService studyAssistantService,
            SupportQuestionChannelAccessClient channelAccessClient,
            SupportQuestionClient supportQuestionClient,
            CourseResourceCatalogClient courseResourceCatalogClient,
            CourseResourceContentClient courseResourceContentClient,
            ApprovedFaqClient approvedFaqClient,
            GroundingEngine groundingEngine,
            AiQuotaEnforcementService aiQuotaEnforcementService,
            StudyAssistantAnswerPersistenceService answerPersistenceService,
            StudyAssistantAnswerRepository answerRepository,
            Clock clock
    ) {
        this.studyAssistantService = studyAssistantService;
        this.channelAccessClient = channelAccessClient;
        this.supportQuestionClient = supportQuestionClient;
        this.courseResourceCatalogClient = courseResourceCatalogClient;
        this.courseResourceContentClient = courseResourceContentClient;
        this.approvedFaqClient = approvedFaqClient;
        this.groundingEngine = groundingEngine;
        this.aiQuotaEnforcementService = aiQuotaEnforcementService;
        this.answerPersistenceService = answerPersistenceService;
        this.answerRepository = answerRepository;
        this.clock = clock;
    }

    public StudyAssistantAnswer answerSupportQuestion(
            UUID channelId,
            UUID supportQuestionId,
            UUID learnerUserId
    ) {
        SupportQuestionChannelAccess access = channelAccessClient.requireAccess(channelId, learnerUserId);
        if (!access.canPostSupportQuestion()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only enrolled learners can invoke the AI Study Assistant"
            );
        }

        SupportQuestion supportQuestion = supportQuestionClient.getSupportQuestion(
                channelId,
                supportQuestionId,
                learnerUserId
        );

        Optional<StudyAssistantAnswer> existingAnswer =
                answerRepository.findBySupportQuestionId(supportQuestionId);
        if (existingAnswer.isPresent()) {
            return reconcileExistingAnswer(channelId, supportQuestionId, learnerUserId, existingAnswer.get());
        }

        if (!"UNANSWERED".equals(supportQuestion.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered");
        }
        if (!supportQuestion.senderUserId().equals(learnerUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the Support Question author can invoke the AI Study Assistant"
            );
        }

        Presence presence = studyAssistantService.findPresence(access.studyServerId(), learnerUserId);
        if (!presence.installed()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "AI Study Assistant is not installed in this Study Server"
            );
        }

        boolean channelGranted = presence.grants().stream()
                .anyMatch(grant -> grant.grantType() == GrantType.COURSE_CHANNEL
                        && grant.grantTargetId().equals(channelId));
        if (!channelGranted) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "AI Study Assistant is not granted for this Course Channel"
            );
        }

        aiQuotaEnforcementService.requireQuotaAvailable(access.studyServerId(), learnerUserId);

        Set<UUID> grantedResourceIds = presence.grants().stream()
                .filter(grant -> grant.grantType() == GrantType.COURSE_RESOURCE)
                .map(StudyAssistantGrant::grantTargetId)
                .collect(Collectors.toSet());

        List<GroundingSource> groundingSources = new ArrayList<>();
        for (CourseResourceSummary resource : courseResourceCatalogClient.listAiApprovedCourseResources(
                access.courseId(),
                learnerUserId
        )) {
            if (!grantedResourceIds.contains(resource.id())) {
                continue;
            }

            try {
                byte[] content = courseResourceContentClient.downloadContent(resource.id(), learnerUserId);
                String textContent = decodeTextContent(content, resource.fileName());
                if (!textContent.isBlank()) {
                    groundingSources.add(new GroundingSource(resource.id(), resource.title(), textContent));
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.NOT_FOUND
                        || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                    continue;
                }
                throw exception;
            } catch (RuntimeException exception) {
                continue;
            }
        }

        try {
            for (ApprovedFaqClient.ApprovedFaqSummary approvedFaq : approvedFaqClient.listApprovedFaqs(
                    access.courseId(),
                    learnerUserId
            )) {
                if (approvedFaq.question() == null || approvedFaq.answer() == null) {
                    continue;
                }
                String textContent = approvedFaq.question() + "\n\n" + approvedFaq.answer();
                if (!textContent.isBlank()) {
                    groundingSources.add(new GroundingSource(
                            approvedFaq.id(),
                            "FAQ: " + approvedFaq.question(),
                            textContent
                    ));
                }
            }
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND
                    && exception.getStatusCode() != HttpStatus.FORBIDDEN
                    && exception.getStatusCode() != HttpStatus.BAD_GATEWAY) {
                throw exception;
            }
        } catch (RuntimeException exception) {
            // FAQ grounding is supplemental; continue without FAQ sources when message-service is unavailable.
        }

        GroundingResult groundingResult = groundingEngine.answer(supportQuestion.body(), groundingSources);
        InvocationType invocationType = groundingResult.handoffRecommended()
                ? InvocationType.LOW_CONFIDENCE_HANDOFF
                : InvocationType.GROUNDED_ANSWER;

        List<StudyAssistantAnswerSource> sources = groundingResult.citations().stream()
                .map(citation -> new StudyAssistantAnswerSource(
                        UUID.randomUUID(),
                        citation.resourceId(),
                        citation.resourceTitle(),
                        citation.excerpt()
                ))
                .toList();

        StudyAssistantAnswer answer = new StudyAssistantAnswer(
                UUID.randomUUID(),
                supportQuestionId,
                channelId,
                access.studyServerId(),
                learnerUserId,
                supportQuestion.body(),
                groundingResult.answerBody(),
                groundingResult.confidence(),
                groundingResult.handoffRecommended(),
                sources,
                clock.instant()
        );

        StudyAssistantAnswer savedAnswer = answerPersistenceService.saveAnswer(answer, invocationType);

        String updatedStatus = statusForConfidence(groundingResult.confidence());
        supportQuestionClient.updateStatus(channelId, supportQuestionId, learnerUserId, updatedStatus);

        return savedAnswer;
    }

    private StudyAssistantAnswer reconcileExistingAnswer(
            UUID channelId,
            UUID supportQuestionId,
            UUID learnerUserId,
            StudyAssistantAnswer existingAnswer
    ) {
        SupportQuestion current = supportQuestionClient.getSupportQuestion(
                channelId,
                supportQuestionId,
                learnerUserId
        );
        if ("UNANSWERED".equals(current.status())) {
            supportQuestionClient.updateStatus(
                    channelId,
                    supportQuestionId,
                    learnerUserId,
                    statusForConfidence(existingAnswer.confidence())
            );
        }
        return existingAnswer;
    }

    private static String statusForConfidence(AnswerConfidence confidence) {
        return confidence == AnswerConfidence.HIGH ? "AI_ANSWERED" : "AI_LOW_CONFIDENCE";
    }

    private static String decodeTextContent(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            return "";
        }

        String lowerFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!(lowerFileName.endsWith(".md")
                || lowerFileName.endsWith(".txt")
                || lowerFileName.endsWith(".markdown"))) {
            return "";
        }

        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
