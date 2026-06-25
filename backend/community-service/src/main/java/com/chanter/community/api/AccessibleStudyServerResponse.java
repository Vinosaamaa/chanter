package com.chanter.community.api;

import com.chanter.community.domain.AccessibleStudyServer;
import java.util.UUID;

public record AccessibleStudyServerResponse(UUID id, String name) {

    static AccessibleStudyServerResponse from(AccessibleStudyServer studyServer) {
        return new AccessibleStudyServerResponse(studyServer.id(), studyServer.name());
    }
}
