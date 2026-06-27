package com.chanter.search.application;

import java.util.List;
import java.util.UUID;

public interface CommunityNavigationClient {

    StudyServerNavigation fetchNavigation(UUID studyServerId, UUID viewerUserId);

    record StudyServerNavigation(
            UUID studyServerId,
            String studyServerName,
            List<CourseSummary> courses
    ) {
    }

    record CourseSummary(UUID id, String title) {
    }
}
