package com.chanter.agent.api;

import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.application.GroundedSupportQuestionService.AnswerView;
import com.chanter.agent.application.LlmExecution;
import com.chanter.agent.application.LlmProviderException;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import java.io.IOException;
import java.util.UUID;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/course-channels/{channelId}/support-questions/{supportQuestionId}")
public class GroundedSupportQuestionController {

    private final GroundedSupportQuestionService groundedSupportQuestionService;

    public GroundedSupportQuestionController(GroundedSupportQuestionService groundedSupportQuestionService) {
        this.groundedSupportQuestionService = groundedSupportQuestionService;
    }

    @PostMapping("/assistant-answer")
    public ResponseEntity<AssistantAnswerResponse> answerSupportQuestion(
            @PathVariable UUID channelId,
            @PathVariable UUID supportQuestionId,
            @RequestHeader(AuthHeaders.USER_ID) UUID learnerUserId,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String answerMode
    ) {
        StudyAssistantAnswer answer = groundedSupportQuestionService.answerSupportQuestion(
                channelId,
                supportQuestionId,
                learnerUserId,
                modelId,
                answerMode
        );
        return ResponseEntity.ok(toResponse(answer, learnerUserId));
    }

    /** Streams validated evidence during generation; the complete event is the authoritative persisted answer. */
    @PostMapping(value = "/assistant-answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssistantAnswer(
            @PathVariable UUID channelId,
            @PathVariable UUID supportQuestionId,
            @RequestHeader(AuthHeaders.USER_ID) UUID learnerUserId,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String answerMode
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        LlmExecution execution = new LlmExecution(Duration.ofSeconds(120));
        emitter.onTimeout(execution::cancel);
        emitter.onError(ignored -> execution.cancel());
        emitter.onCompletion(execution::cancel);

        Thread.startVirtualThread(() -> {
            try (execution) {
                emitter.send(SseEmitter.event().name("status").data("retrieving"));
                StudyAssistantAnswer answer = groundedSupportQuestionService.answerSupportQuestion(channelId, supportQuestionId, learnerUserId,
                        modelId, answerMode, execution, token -> {
                            execution.check();
                            try { emitter.send(SseEmitter.event().name("token").data(token)); }
                            catch (IOException | IllegalStateException disconnected) {
                                execution.cancel();
                                throw new LlmProviderException(LlmProviderException.Outcome.CANCELLED);
                            }
                        });
                execution.check();
                emitter.send(SseEmitter.event().name("complete").data(toResponse(answer, learnerUserId)));
                emitter.complete();
            } catch (Exception failure) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(StreamError.from(failure)));
                    emitter.complete();
                } catch (IOException | IllegalStateException disconnected) { emitter.complete(); }
            }
        });
        return emitter;
    }

    record StreamError(String code, int status, String message) {
        static StreamError from(Exception failure) {
            if (failure instanceof ResponseStatusException response) {
                int status = response.getStatusCode().value();
                return switch (status) {
                    case 403 -> new StreamError("ACCESS_DENIED", status, "AI answer access is no longer available.");
                    case 404 -> new StreamError("NOT_AVAILABLE", status, "The question or assistant is no longer available.");
                    case 409 -> new StreamError("CONFLICT", status, "This answer cannot start. Check the selected capability and the question's current status.");
                    case 429 -> new StreamError("QUOTA_EXHAUSTED", status, "The Study Server's AI budget is exhausted.");
                    default -> new StreamError("UNAVAILABLE", 503, "The answer could not be completed. An Instructor or TA can help.");
                };
            }
            if (failure instanceof LlmProviderException provider) return new StreamError(provider.outcome().name(), 503,
                    "The AI request did not complete. An Instructor or TA can help.");
            return new StreamError("UNAVAILABLE", 503, "The answer could not be completed. An Instructor or TA can help.");
        }
    }

    @GetMapping("/assistant-answer")
    public AssistantAnswerResponse getAssistantAnswer(
            @PathVariable UUID channelId,
            @PathVariable UUID supportQuestionId,
            @RequestHeader(AuthHeaders.USER_ID) UUID viewerUserId
    ) {
        StudyAssistantAnswer answer = groundedSupportQuestionService.findAnswer(
                channelId,
                supportQuestionId,
                viewerUserId
        );
        return toResponse(answer, viewerUserId);
    }

    @PostMapping("/assistant-answer/helpful")
    public AssistantAnswerResponse markHelpful(
            @PathVariable UUID channelId,
            @PathVariable UUID supportQuestionId,
            @RequestHeader(AuthHeaders.USER_ID) UUID viewerUserId
    ) {
        AnswerView view = groundedSupportQuestionService.markHelpful(channelId, supportQuestionId, viewerUserId);
        return toResponse(view);
    }

    private AssistantAnswerResponse toResponse(StudyAssistantAnswer answer, UUID viewerUserId) {
        return toResponse(groundedSupportQuestionService.toAnswerView(answer, viewerUserId));
    }

    private static AssistantAnswerResponse toResponse(AnswerView view) {
        String supportQuestionStatus = view.answer().confidence() == AnswerConfidence.HIGH
                ? "AI_ANSWERED"
                : "AI_LOW_CONFIDENCE";
        return AssistantAnswerResponse.from(
                view.answer(),
                supportQuestionStatus,
                view.audit(),
                view.helpfulMarked(),
                view.helpfulCount()
        );
    }
}
