package com.chanter.media.application;

import com.chanter.media.domain.CourseResource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseResourceRepository {

    CourseResource save(CourseResource courseResource);

    Optional<CourseResource> findById(UUID resourceId);

    List<CourseResource> findByCourseId(UUID courseId);

    void deleteById(UUID resourceId);
}
