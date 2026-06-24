package com.chanter.agent.api;

import com.chanter.common.ServiceInfo;
import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.domain.AnswerConfidence;
import com.chanter.agent.domain.StudyAssistantAnswer;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @Valid @RequestBody InvokeAssistantAnswerRequest request
    ) {
        StudyAssistantAnswer answer = groundedSupportQuestionService.answerSupportQuestion(
                channelId,
                supportQuestionId,
                request.learnerUserId()
        );
        String supportQuestionStatus = answer.confidence() == AnswerConfidence.HIGH
                ? "AI_ANSWERED"
                : "AI_LOW_CONFIDENCE";

        return ResponseEntity.ok(AssistantAnswerResponse.from(answer, supportQuestionStatus));
    }
}
