package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.SocialMembershipService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX)
public class SocialMembershipController {

    private final SocialMembershipService socialMembershipService;

    public SocialMembershipController(SocialMembershipService socialMembershipService) {
        this.socialMembershipService = socialMembershipService;
    }

    @GetMapping("/users/{peerUserId}/co-membership")
    public CoMembershipResponse findCoMembership(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId,
            @PathVariable UUID peerUserId
    ) {
        return new CoMembershipResponse(
                socialMembershipService.shareStudyServerMembership(userId, peerUserId)
        );
    }

    @GetMapping("/social/co-members")
    public CoMemberListResponse findCoMembers(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return CoMemberListResponse.from(socialMembershipService.findCoMembers(userId));
    }
}
