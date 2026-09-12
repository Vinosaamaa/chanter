package com.chanter.agent.application;

import com.chanter.agent.application.CourseResourceCatalogClient.CourseResourceSummary;
import com.chanter.agent.application.GroundingEngine.GroundingResult;
import com.chanter.agent.application.GroundingEngine.GroundingSource;
import com.chanter.agent.application.GroundingEngine.SourceCitation;
import com.chanter.agent.application.StudyAssistantService.Presence;
import com.chanter.agent.application.SupportQuestionChannelAccessClient.SupportQuestionChannelAccess;
import com.chanter.agent.application.SupportQuestionClient.SupportQuestion;
import com.chanter.agent.application.VectorRetrievalService.RankedChunk;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.GrantType;
import com.chanter.agent.domain.InvocationType;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.agent.domain.StudyAssistantAnswerAudit;
import com.chanter.agent.domain.StudyAssistantAnswerSource;
import com.chanter.agent.domain.StudyAssistantGrant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    private final VectorRetrievalService vectorRetrievalService;
    private final ObjectProvider<RagGroundingEngine> ragGroundingEngine;
    private final ObjectProvider<KeywordGroundingEngine> keywordGroundingEngine;
    private final AgentRuntimeService agentRuntimeService;
    private final LlmModelCatalog modelCatalog;
    private final AiEvidenceAuthorization evidenceAuthorization;
    private final AiQuotaEnforcementService aiQuotaEnforcementService;
    private final StudyAssistantAnswerPersistenceService answerPersistenceService;
    private final StudyAssistantAnswerRepository answerRepository;
    private final Clock clock;
    private final String groundingEngineMode;
    private final int retrievalTopK;

    public GroundedSupportQuestionService(
            StudyAssistantService studyAssistantService,
            SupportQuestionChannelAccessClient channelAccessClient,
            SupportQuestionClient supportQuestionClient,
            CourseResourceCatalogClient courseResourceCatalogClient,
            CourseResourceContentClient courseResourceContentClient,
            ApprovedFaqClient approvedFaqClient,
            VectorRetrievalService vectorRetrievalService,
            ObjectProvider<RagGroundingEngine> ragGroundingEngine,
            ObjectProvider<KeywordGroundingEngine> keywordGroundingEngine,
            AgentRuntimeService agentRuntimeService,
            LlmModelCatalog modelCatalog,
            AiEvidenceAuthorization evidenceAuthorization,
            AiQuotaEnforcementService aiQuotaEnforcementService,
            StudyAssistantAnswerPersistenceService answerPersistenceService,
            StudyAssistantAnswerRepository answerRepository,
            Clock clock,
            @Value("${chanter.grounding.engine:rag}") String groundingEngineMode,
            @Value("${chanter.grounding.retrieval-top-k:5}") int retrievalTopK
    ) {
        this.studyAssistantService = studyAssistantService;
        this.channelAccessClient = channelAccessClient;
        this.supportQuestionClient = supportQuestionClient;
        this.courseResourceCatalogClient = courseResourceCatalogClient;
        this.courseResourceContentClient = courseResourceContentClient;
        this.approvedFaqClient = approvedFaqClient;
        this.vectorRetrievalService = vectorRetrievalService;
        this.ragGroundingEngine = ragGroundingEngine;
        this.keywordGroundingEngine = keywordGroundingEngine;
        this.agentRuntimeService = agentRuntimeService;
        this.modelCatalog = modelCatalog;
        this.evidenceAuthorization = evidenceAuthorization;
        this.aiQuotaEnforcementService = aiQuotaEnforcementService;
        this.answerPersistenceService = answerPersistenceService;
        this.answerRepository = answerRepository;
        this.clock = clock;
        this.groundingEngineMode = groundingEngineMode == null ? "rag" : groundingEngineMode;
        this.retrievalTopK = retrievalTopK < 1 ? 5 : retrievalTopK;
    }

    public StudyAssistantAnswer answerSupportQuestion(
            UUID channelId,
            UUID supportQuestionId,
            UUID learnerUserId
    ) {
        return answerSupportQuestion(channelId, supportQuestionId, learnerUserId, null);
    }

    public StudyAssistantAnswer answerSupportQuestion(UUID channelId, UUID supportQuestionId, UUID learnerUserId, String modelId) {
        return answerSupportQuestion(channelId, supportQuestionId, learnerUserId, modelId, null);
    }

    public StudyAssistantAnswer answerSupportQuestion(UUID channelId, UUID supportQuestionId, UUID learnerUserId, String modelId, String answerMode) {
        try (var execution = new LlmExecution(Duration.ofSeconds(120))) {
            return answerSupportQuestion(channelId, supportQuestionId, learnerUserId, modelId, answerMode, execution, ignored -> {});
        }
    }

    public StudyAssistantAnswer answerSupportQuestion(UUID channelId, UUID supportQuestionId, UUID learnerUserId,
            String modelId, String answerMode, LlmExecution execution, Consumer<String> chunks) {
        execution.check();
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

        String selectedModel = modelCatalog.select(modelId, access.courseId());
        String mode = AiAnswerMode.resolve(answerMode, selectedModel);
        String selection = "source-only".equals(mode) ? LlmModelCatalog.SOURCE_ONLY : selectedModel;
        if (!supportQuestion.senderUserId().equals(learnerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Support Question author can invoke the AI Study Assistant");
        }

        Optional<StudyAssistantAnswer> existingAnswer =
                answerRepository.findBySupportQuestionId(supportQuestionId);
        if (existingAnswer.isPresent()) {
            StudyAssistantAnswer answer = existingAnswer.get();
            if (!answer.channelId().equals(channelId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            evidenceAuthorization.requireCurrent(channelId, learnerUserId, citations(answer));
            execution.check();
            chunks.accept(answer.answerBody());
            return reconcileExistingAnswer(channelId, supportQuestionId, learnerUserId, answer);
        }

        if (!"UNANSWERED".equals(supportQuestion.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Support Question is no longer unanswered");
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

        Map<UUID, String> resourceTitles = new HashMap<>();
        List<GroundingSource> downloadedSources = new ArrayList<>();
        for (CourseResourceSummary resource : courseResourceCatalogClient.listAiApprovedCourseResources(
                access.courseId(),
                learnerUserId
        )) {
            if (!grantedResourceIds.contains(resource.id()) || !resource.aiApproved() || !resource.courseId().equals(access.courseId())) {
                continue;
            }
            resourceTitles.put(resource.id(), resource.title());

            try {
                byte[] content = courseResourceContentClient.downloadContent(resource.id(), learnerUserId);
                String textContent = decodeTextContent(content, resource.fileName());
                if (!textContent.isBlank()) {
                    downloadedSources.add(new GroundingSource(resource.id(), resource.title(), textContent));
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.NOT_FOUND
                        || exception.getStatusCode() == HttpStatus.FORBIDDEN
                        || exception.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                    continue;
                }
                throw exception;
            } catch (RuntimeException exception) {
                continue;
            }
        }

        // A grant alone is insufficient: stale vectors must also belong to currently approved Course material.
        grantedResourceIds.retainAll(resourceTitles.keySet());

        List<GroundingSource> faqSources = loadFaqSources(access.courseId(), learnerUserId);

        GroundingResult groundingResult = ground(
                supportQuestion.body(),
                grantedResourceIds,
                resourceTitles,
                downloadedSources,
                faqSources
        );
        if (!LlmModelCatalog.SOURCE_ONLY.equals(selection)) {
            groundingResult = new GroundingResult(groundingResult.answerBody(), groundingResult.confidence(),
                    groundingResult.citations().stream().map(c -> new SourceCitation(c.resourceId(), c.resourceTitle(),
                            AiEvidenceAuthorization.plainExcerpt(c.excerpt()))).toList());
        }
        List<SourceCitation> initialEvidence = groundingResult.citations();
        Runnable reauthorize = () -> {
            execution.check();
            var current = channelAccessClient.requireAccess(channelId, learnerUserId);
            if (!current.canPostSupportQuestion() || !access.courseId().equals(current.courseId())
                    || !access.studyServerId().equals(current.studyServerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI answer access changed");
            }
            modelCatalog.select(selection, current.courseId());
            evidenceAuthorization.requireCurrent(channelId, learnerUserId, initialEvidence);
        };
        AgentRuntimeService.OrchestratedAnswer orchestrated = agentRuntimeService.orchestrate(supportQuestion.body(), groundingResult,
                new AgentRuntimeService.Invocation(access.studyServerId(), supportQuestionId, learnerUserId, selection, reauthorize), execution, chunks);
        groundingResult = orchestrated.result();
        execution.check();
        evidenceAuthorization.requireCurrent(channelId, learnerUserId, groundingResult.citations());

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

        StudyAssistantAnswer savedAnswer = answerPersistenceService.saveAnswer(
                answer,
                invocationType,
                orchestrated.providerId(),
                orchestrated.modelId(),
                orchestrated.llmUsed()
        );

        String updatedStatus = statusForConfidence(groundingResult.confidence());
        supportQuestionClient.updateStatus(channelId, supportQuestionId, learnerUserId, updatedStatus);

        return savedAnswer;
    }

    private GroundingResult ground(
            String question,
            Set<UUID> grantedResourceIds,
            Map<UUID, String> resourceTitles,
            List<GroundingSource> downloadedSources,
            List<GroundingSource> faqSources
    ) {
        if ("keyword".equalsIgnoreCase(groundingEngineMode)) {
            KeywordGroundingEngine keyword = keywordGroundingEngine.getIfAvailable();
            if (keyword == null) {
                keyword = new KeywordGroundingEngine();
            }
            List<GroundingSource> all = new ArrayList<>(downloadedSources);
            all.addAll(faqSources);
            return keyword.answer(question, all);
        }

        RagGroundingEngine rag = ragGroundingEngine.getIfAvailable();
        if (rag == null) {
            rag = new RagGroundingEngine(0.12);
        }

        List<RankedChunk> ranked = vectorRetrievalService.retrieve(question, grantedResourceIds, retrievalTopK);
        if (!ranked.isEmpty()) {
            return rag.answer(question, ranked, faqSources, resourceTitles);
        }

        List<GroundingSource> fallbackSources = new ArrayList<>(downloadedSources);
        fallbackSources.addAll(faqSources);
        if (!fallbackSources.isEmpty()) {
            return rag.answerWithKeywordFallback(question, fallbackSources);
        }
        return rag.answer(question, List.of(), faqSources, resourceTitles);
    }

    private List<GroundingSource> loadFaqSources(UUID courseId, UUID learnerUserId) {
        List<GroundingSource> faqSources = new ArrayList<>();
        try {
            for (ApprovedFaqClient.ApprovedFaqSummary approvedFaq : approvedFaqClient.listApprovedFaqs(
                    courseId,
                    learnerUserId
            )) {
                if (approvedFaq.question() == null || approvedFaq.answer() == null) {
                    continue;
                }
                String textContent = approvedFaq.question() + "\n\n" + approvedFaq.answer();
                if (!textContent.isBlank()) {
                    faqSources.add(new GroundingSource(
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
            // FAQ grounding is supplemental
        }
        return faqSources;
    }

    public StudyAssistantAnswer findAnswer(
            UUID channelId,
            UUID supportQuestionId,
            UUID viewerUserId
    ) {
        SupportQuestionChannelAccess access = channelAccessClient.requireAccess(channelId, viewerUserId);
        SupportQuestion supportQuestion = supportQuestionClient.getSupportQuestion(
                channelId,
                supportQuestionId,
                viewerUserId
        );
        if (!supportQuestion.senderUserId().equals(viewerUserId)
                && !access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support Question answer access denied");
        }

        StudyAssistantAnswer answer = answerRepository.findBySupportQuestionId(supportQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assistant answer not found"));
        if (!answer.channelId().equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assistant answer not found");
        }
        evidenceAuthorization.requireCurrent(channelId, viewerUserId, citations(answer));
        return answer;
    }

    private static List<SourceCitation> citations(StudyAssistantAnswer answer) {
        return answer.sources().stream().map(source -> new SourceCitation(source.resourceId(), source.resourceTitle(), source.excerpt())).toList();
    }

    public AnswerView toAnswerView(StudyAssistantAnswer answer, UUID viewerUserId) {
        StudyAssistantAnswerAudit audit = answerRepository.findAuditByAnswerId(answer.id()).orElse(null);
        boolean helpfulMarked = answerRepository.isHelpfulMarked(answer.id(), viewerUserId);
        int helpfulCount = answerRepository.countHelpful(answer.id());
        return new AnswerView(answer, audit, helpfulMarked, helpfulCount);
    }

    public AnswerView markHelpful(UUID channelId, UUID supportQuestionId, UUID viewerUserId) {
        StudyAssistantAnswer answer = findAnswer(channelId, supportQuestionId, viewerUserId);
        if (!answer.learnerUserId().equals(viewerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the question author can mark the answer helpful");
        }
        answerRepository.markHelpful(answer.id(), viewerUserId);
        return toAnswerView(answer, viewerUserId);
    }

    public record AnswerView(
            StudyAssistantAnswer answer,
            StudyAssistantAnswerAudit audit,
            boolean helpfulMarked,
            int helpfulCount
    ) {
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
