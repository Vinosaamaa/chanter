package com.chanter.agent.api;

import com.chanter.common.ServiceInfo;
import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        // Status strings must match SupportQuestionStatus enum in message-service.
        String supportQuestionStatus = answer.confidence() == AnswerConfidence.HIGH
                ? "AI_ANSWERED"
                : "AI_LOW_CONFIDENCE";

        return ResponseEntity.ok(AssistantAnswerResponse.from(answer, supportQuestionStatus));
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
        String supportQuestionStatus = answer.confidence() == AnswerConfidence.HIGH
                ? "AI_ANSWERED"
                : "AI_LOW_CONFIDENCE";
        return AssistantAnswerResponse.from(answer, supportQuestionStatus);
    }
}
