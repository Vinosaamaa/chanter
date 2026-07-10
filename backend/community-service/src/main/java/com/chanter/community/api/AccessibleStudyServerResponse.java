package com.chanter.community.api;

import com.chanter.community.domain.AccessibleStudyServer;
import java.util.UUID;

public record AccessibleStudyServerResponse(
        UUID id,
        String name,
        boolean owner,
        int courseCount,
        int memberCount
) {

    static AccessibleStudyServerResponse from(AccessibleStudyServer studyServer) {
        return new AccessibleStudyServerResponse(
                studyServer.id(),
                studyServer.name(),
                studyServer.owner(),
                studyServer.courseCount(),
                studyServer.memberCount()
        );
    }
}
