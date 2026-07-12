package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CohortTaQueueAccess;
import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.CourseResourceAccess;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course);

    void enrollLearner(UUID cohortId, UUID learnerUserId, UUID enrolledByUserId, Instant enrolledAt);

    CohortEnrollmentList listCohortEnrollments(UUID cohortId, int limit, int offset, String learnerSearch);

    Optional<UUID> findCohortInviteCode(UUID cohortId);

    Optional<UUID> findCohortInviteCodeForInstructor(UUID cohortId, UUID instructorUserId);

    boolean cohortExists(UUID cohortId);

    boolean cohortHasInstructor(UUID cohortId, UUID instructorUserId);

    boolean courseChannelExists(UUID channelId);

    Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId);

    Optional<SupportQuestionChannelAccess> findSupportQuestionChannelAccess(UUID channelId, UUID userId);

    boolean courseExists(UUID courseId);

    Optional<CourseResourceAccess> findCourseResourceAccess(UUID courseId, UUID userId);

    Optional<CohortTaQueueAccess> findCohortTaQueueAccess(UUID cohortId, UUID userId);

    Optional<CohortOfficeHoursAccess> findCohortOfficeHoursAccess(UUID cohortId, UUID userId);

    boolean isStudyServerOwner(UUID studyServerId, UUID userId);

    boolean isInstructorOnAnyCourseInStudyServer(UUID studyServerId, UUID userId);

    boolean studyServerExists(UUID studyServerId);

    Optional<StudyAssistantGrantCandidates> findGrantCandidates(UUID studyServerId);

    Optional<StudyAssistantViewerScope> findViewerScope(UUID studyServerId, UUID userId);

    List<AccessibleStudyServer> listAccessibleStudyServers(UUID userId);
}
