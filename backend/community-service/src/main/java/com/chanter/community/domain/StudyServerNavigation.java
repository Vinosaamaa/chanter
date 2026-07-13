package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record StudyServerNavigation(
        UUID studyServerId,
        String studyServerName,
        boolean canViewFullCatalog,
        StudyServerCapabilities capabilities,
        List<StudyServerChannel> studyServerChannels,
        List<StudyServerNavigationCourse> courses
) {
}
