package com.chanter.message.application;

import java.util.UUID;

public interface CourseChannelAccessClient {

    CourseChannelAccess requireAccess(UUID channelId, UUID userId);
}
