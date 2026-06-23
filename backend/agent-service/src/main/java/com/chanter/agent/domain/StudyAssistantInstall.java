package com.chanter.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record StudyAssistantInstall(
        UUID id,
        UUID studyServerId,
        UUID installedByUserId,
        Instant installedAt
) {
}
