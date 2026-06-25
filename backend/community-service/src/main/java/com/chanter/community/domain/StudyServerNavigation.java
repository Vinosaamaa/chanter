package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record StudyServerNavigation(
        UUID studyServerId,
        String studyServerName,
        boolean canViewFullCatalog,
        List<StudyServerChannel> studyServerChannels,
        List<GrantCandidateCourse> courses
) {
}
