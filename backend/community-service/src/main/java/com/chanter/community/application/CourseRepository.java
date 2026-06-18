package com.chanter.community.application;

import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course);

    void enrollLearner(UUID cohortId, UUID learnerUserId, UUID enrolledByUserId, Instant enrolledAt);

    boolean cohortExists(UUID cohortId);

    boolean cohortHasInstructor(UUID cohortId, UUID instructorUserId);

    boolean courseChannelExists(UUID channelId);

    Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId);

    Optional<SupportQuestionChannelAccess> findSupportQuestionChannelAccess(UUID channelId, UUID userId);
}
