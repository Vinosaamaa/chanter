package com.chanter.community.domain;

import java.util.List;
import java.util.UUID;

public record StudyAssistantGrantCandidates(
        UUID studyServerId,
        List<StudyServerChannel> studyServerChannels,
        List<GrantCandidateCourse> courses
) {
}
