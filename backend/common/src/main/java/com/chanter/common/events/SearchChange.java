package com.chanter.common.events;

import java.util.UUID;

public record SearchChange(String type, UUID sourceId, UUID studyServerId, UUID courseId,
        UUID cohortId, UUID channelId, String channelScope, String title, String body, String href, boolean deleted) { }
