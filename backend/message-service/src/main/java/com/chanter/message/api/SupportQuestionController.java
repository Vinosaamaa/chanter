package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.message.application.SupportQuestionService;
import com.chanter.message.domain.SupportQuestion;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/course-channels")
public class SupportQuestionController {

    private final SupportQuestionService supportQuestionService;

    public SupportQuestionController(SupportQuestionService supportQuestionService) {
        this.supportQuestionService = supportQuestionService;
    }

    @PostMapping("/{channelId}/support-questions")
    public ResponseEntity<SupportQuestionResponse> postSupportQuestion(
            @PathVariable UUID channelId,
            @Valid @RequestBody CreateSupportQuestionRequest request
    ) {
        SupportQuestion supportQuestion = supportQuestionService.postSupportQuestion(
                channelId,
                request.senderUserId(),
                request.body(),
                request.idempotencyKey()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{supportQuestionId}")
                .buildAndExpand(supportQuestion.id())
                .toUri();

        return ResponseEntity.created(location).body(SupportQuestionResponse.from(supportQuestion));
    }

    @GetMapping("/{channelId}/support-questions")
    public SupportQuestionListResponse listUnansweredSupportQuestions(
            @PathVariable UUID channelId,
            @RequestParam UUID viewerUserId
    ) {
        List<SupportQuestionResponse> supportQuestions = supportQuestionService
                .listUnansweredSupportQuestions(channelId, viewerUserId)
                .stream()
                .map(SupportQuestionResponse::from)
                .toList();

        return new SupportQuestionListResponse(supportQuestions);
    }
}
