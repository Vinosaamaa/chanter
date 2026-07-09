package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.message.application.SocialMessagingService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/direct-message-calls")
public class DirectMessageCallController {

    private final SocialMessagingService socialMessagingService;

    public DirectMessageCallController(SocialMessagingService socialMessagingService) {
        this.socialMessagingService = socialMessagingService;
    }

    @GetMapping("/eligibility")
    public ResponseEntity<Void> requireEligibility(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID callerUserId,
            @RequestParam UUID peerUserId
    ) {
        socialMessagingService.requireDirectMessageCallAccess(callerUserId, peerUserId);
        return ResponseEntity.noContent().build();
    }
}
