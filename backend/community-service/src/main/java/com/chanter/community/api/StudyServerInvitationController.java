package com.chanter.community.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.community.application.StudyServerService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/study-server-invitations")
public class StudyServerInvitationController {

    private final StudyServerService studyServerService;

    public StudyServerInvitationController(StudyServerService studyServerService) {
        this.studyServerService = studyServerService;
    }

    @GetMapping
    public List<UserStudyServerInvitationResponse> listPendingInvitations(
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
    ) {
        return studyServerService.findPendingInvitationsForUser(userId);
    }
}
