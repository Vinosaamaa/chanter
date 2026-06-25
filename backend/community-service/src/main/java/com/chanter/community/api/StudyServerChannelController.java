package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.StudyServerService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-server-channels")
public class StudyServerChannelController {

    private final StudyServerService studyServerService;

    public StudyServerChannelController(StudyServerService studyServerService) {
        this.studyServerService = studyServerService;
    }

    @PostMapping("/{channelId}/voice-presences")
    public ResponseEntity<VoicePresenceResponse> joinVoiceChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID memberUserId
    ) {
        VoicePresenceResponse response = VoicePresenceResponse.from(
                studyServerService.joinVoiceChannel(channelId, memberUserId)
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{memberUserId}")
                .buildAndExpand(response.memberUserId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{channelId}/voice-presences")
    public VoicePresenceListResponse getVoicePresences(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId
    ) {
        return VoicePresenceListResponse.from(studyServerService.findVoicePresences(channelId, viewerUserId));
    }

    @DeleteMapping("/{channelId}/voice-presences")
    public ResponseEntity<Void> leaveVoiceChannel(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID memberUserId
    ) {
        studyServerService.leaveVoiceChannel(channelId, memberUserId);
        return ResponseEntity.noContent().build();
    }
}
