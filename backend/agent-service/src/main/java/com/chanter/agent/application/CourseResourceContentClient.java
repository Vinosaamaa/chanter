package com.chanter.agent.application;

import java.util.UUID;

public interface CourseResourceContentClient {

    byte[] downloadContent(UUID resourceId, UUID viewerUserId);
}
