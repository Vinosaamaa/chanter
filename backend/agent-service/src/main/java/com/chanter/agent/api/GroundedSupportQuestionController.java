package com.chanter.agent.api;

import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.application.GroundedSupportQuestionService.AnswerView;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthHeaders;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
            @RequestHeader(AuthHeaders.USER_ID) UUID learnerUserId
    ) {
        StudyAssistantAnswer answer = groundedSupportQuestionService.answerSupportQuestion(
                channelId,
                supportQuestionId,
                learnerUserId
        );
        return ResponseEntity.ok(toResponse(answer, learnerUserId));
    }

    /**
     * SSE streaming path (#98/#100). Computes the full answer (RAG + optional LLM), then emits
     * {@code token} events followed by a final {@code complete} event with the persisted payload.
     */
    @PostMapping(value = "/assistant-answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssistantAnswer(
            @PathVariable UUID channelId,
            @PathVariable UUID supportQuestionId,
            @RequestHeader(AuthHeaders.USER_ID) UUID learnerUserId
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        StudyAssistantAnswer answer = groundedSupportQuestionService.answerSupportQuestion(
                channelId,
                supportQuestionId,
                learnerUserId
        );
        AssistantAnswerResponse response = toResponse(answer, learnerUserId);

        Thread.startVirtualThread(() -> {
            try {
                String body = answer.answerBody() == null ? "" : answer.answerBody();
                int chunkSize = 24;
                for (int i = 0; i < body.length(); i += chunkSize) {
                    String token = body.substring(i, Math.min(body.length(), i + chunkSize));
                    emitter.send(SseEmitter.event().name("token").data(token));
                }
                emitter.send(SseEmitter.event().name("complete").data(response));
                emitter.complete();
            } catch (IOException | IllegalStateException exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
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
