package com.chanter.media.application;

import java.util.UUID;

public interface CourseResourceAccessClient {

    CourseResourceAccess requireAccess(UUID courseId, UUID userId);
}
