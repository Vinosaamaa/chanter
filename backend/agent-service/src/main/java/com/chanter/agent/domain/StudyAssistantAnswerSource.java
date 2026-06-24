package com.chanter.agent.domain;

import java.util.UUID;

public record StudyAssistantAnswerSource(
        UUID id,
        UUID resourceId,
        String resourceTitle,
        String excerpt
) {
}
