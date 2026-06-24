package com.chanter.message.application;

import java.util.UUID;

public interface CourseResourceAccessClient {

    CourseResourceAccess requireAccess(UUID courseId, UUID userId);
}
