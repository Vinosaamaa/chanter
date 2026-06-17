package com.chanter.community.application;

import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course);

    void enrollLearner(UUID cohortId, UUID learnerUserId, UUID enrolledByUserId, Instant enrolledAt);

    boolean cohortHasInstructor(UUID cohortId, UUID instructorUserId);

    Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId);
}
