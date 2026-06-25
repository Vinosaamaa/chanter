package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.message.application.ChannelMessageService;
import com.chanter.message.domain.ChannelMessage;
import com.chanter.message.domain.ChannelScope;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class ChannelMessageController {

    private final ChannelMessageService channelMessageService;

    public ChannelMessageController(ChannelMessageService channelMessageService) {
        this.channelMessageService = channelMessageService;
    }

    @GetMapping("/study-server-channels/{channelId}/messages")
    public ChannelMessageListResponse listStudyServerChannelMessages(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) UUID afterMessageId
    ) {
        return toListResponse(channelMessageService.listMessages(
                channelId,
                viewerUserId,
                ChannelScope.STUDY_SERVER,
                Optional.ofNullable(since),
                Optional.ofNullable(afterMessageId)
        ));
    }

    @PostMapping("/study-server-channels/{channelId}/messages")
    public ResponseEntity<ChannelMessageResponse> postStudyServerChannelMessage(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID senderUserId,
            @Valid @RequestBody CreateChannelMessageRequest request
    ) {
        return createdResponse(channelMessageService.postMessage(
                channelId,
                senderUserId,
                ChannelScope.STUDY_SERVER,
                request.body()
        ));
    }

    @GetMapping("/course-channels/{channelId}/messages")
    public ChannelMessageListResponse listCourseChannelMessages(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) UUID afterMessageId
    ) {
        return toListResponse(channelMessageService.listMessages(
                channelId,
                viewerUserId,
                ChannelScope.COURSE,
                Optional.ofNullable(since),
                Optional.ofNullable(afterMessageId)
        ));
    }

    @PostMapping("/course-channels/{channelId}/messages")
    public ResponseEntity<ChannelMessageResponse> postCourseChannelMessage(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID senderUserId,
            @Valid @RequestBody CreateChannelMessageRequest request
    ) {
        return createdResponse(channelMessageService.postMessage(
                channelId,
                senderUserId,
                ChannelScope.COURSE,
                request.body()
        ));
    }

    private static ChannelMessageListResponse toListResponse(List<ChannelMessage> messages) {
        return new ChannelMessageListResponse(messages.stream().map(ChannelMessageResponse::from).toList());
    }

    private static ResponseEntity<ChannelMessageResponse> createdResponse(ChannelMessage message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelMessageResponse.from(message));
    }
}
