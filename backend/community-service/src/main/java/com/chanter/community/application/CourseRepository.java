package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
import com.chanter.community.domain.CohortInvitation;
import com.chanter.community.domain.CohortJoinDetails;
import com.chanter.community.domain.Cohort;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseLifecycle;
import com.chanter.community.domain.CourseCatalogCourse;
import com.chanter.community.domain.CourseCatalogFilter;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CohortTaQueueAccess;
import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.CourseResourceAccess;
import com.chanter.community.domain.StudyAssistantGrantCandidates;
import com.chanter.community.domain.StudyAssistantViewerScope;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import com.chanter.community.domain.VoicePresence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course, String description);

    List<CourseCatalogCourse> findPublishedCourseCatalog(
            UUID studyServerId,
            UUID viewerUserId,
            String searchPattern,
            CourseCatalogFilter filter
    );

    CourseChannel saveChannel(CourseChannel channel);

    Optional<CourseChannel> findActiveChannelById(UUID channelId);

    void renameChannel(UUID channelId, String name);

    void archiveChannel(UUID channelId, Instant archivedAt);

    void lockCohortForChannelMutation(UUID cohortId);

    boolean activeChannelNameExists(UUID cohortId, String name);

    boolean activeChannelNameExistsExcluding(UUID cohortId, String name, UUID excludedChannelId);

    VoicePresence saveCourseVoicePresence(
            UUID channelId,
            UUID memberUserId,
            Instant joinedAt,
            Instant expiresAt
    );

    List<VoicePresence> findCourseVoicePresences(UUID channelId, Instant activeAt);

    void deleteCourseVoicePresence(UUID channelId, UUID memberUserId);

    Optional<UUID> findCourseIdByCohortId(UUID cohortId);

    int findNextChannelPosition(UUID cohortId);

    void enrollLearner(UUID cohortId, UUID learnerUserId, UUID enrolledByUserId, Instant enrolledAt);

    CohortEnrollmentList listCohortEnrollments(UUID cohortId, int limit, int offset, String learnerSearch);

    Optional<UUID> findCohortInviteCode(UUID cohortId);

    Optional<CohortJoinDetails> findCohortJoinDetails(UUID cohortId);

    Optional<UUID> findCohortInviteCodeForInstructor(UUID cohortId, UUID instructorUserId);

    boolean cohortExists(UUID cohortId);

    boolean cohortHasInstructor(UUID cohortId, UUID instructorUserId);

    boolean cohortHasRosterViewer(UUID cohortId, UUID viewerUserId);

    boolean cohortHasPeopleManager(UUID cohortId, UUID viewerUserId);

    Optional<UUID> findCohortInstructorUserId(UUID cohortId);

    void addTeachingAssistant(UUID cohortId, UUID userId);

    void removeTeachingAssistant(UUID cohortId, UUID userId);

    List<UUID> findTeachingAssistantUserIds(UUID cohortId);

    boolean cohortHasTeachingAssistant(UUID cohortId, UUID userId);

    boolean cohortHasEnrollments(UUID cohortId, List<UUID> learnerUserIds);

    void assignTeachingAssistant(UUID cohortId, List<UUID> learnerUserIds, UUID teachingAssistantUserId);

    void removeEnrollment(UUID cohortId, UUID learnerUserId);

    CohortInvitation saveInvitation(CohortInvitation invitation);

    List<CohortInvitation> findPendingInvitations(UUID cohortId);

    void cancelInvitation(UUID cohortId, UUID invitationId, java.time.Instant resolvedAt);

    boolean courseChannelExists(UUID channelId);

    Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId);

    Optional<SupportQuestionChannelAccess> findSupportQuestionChannelAccess(UUID channelId, UUID userId);

    boolean courseExists(UUID courseId);

    Optional<CourseResourceAccess> findCourseResourceAccess(UUID courseId, UUID userId);

    Optional<CohortTaQueueAccess> findCohortTaQueueAccess(UUID cohortId, UUID userId);

    Optional<CohortOfficeHoursAccess> findCohortOfficeHoursAccess(UUID cohortId, UUID userId);

    boolean isStudyServerOwner(UUID studyServerId, UUID userId);

    boolean isInstructorOnAnyCourseInStudyServer(UUID studyServerId, UUID userId);

    List<UUID> findInstructedCourseIds(UUID studyServerId, UUID userId);

    List<UUID> findTeachingAssistantCohortIds(UUID studyServerId, UUID userId);

    boolean studyServerExists(UUID studyServerId);

    Optional<StudyAssistantGrantCandidates> findGrantCandidates(UUID studyServerId);

    Optional<StudyAssistantViewerScope> findViewerScope(UUID studyServerId, UUID userId);

    List<AccessibleStudyServer> listAccessibleStudyServers(UUID userId);

    CourseLifecycle saveDraftCourse(
            UUID studyServerId,
            String title,
            String description,
            UUID instructorUserId,
            Instant createdAt
    );

    Optional<CourseLifecycle> findCourseLifecycle(UUID courseId);

    Optional<UUID> findStudyServerIdByCourseId(UUID courseId);

    Cohort addCohortToCourse(UUID courseId, Cohort cohort, List<CourseChannel> channels);

    void assignCourseInstructor(UUID courseId, UUID instructorUserId);

    void setCoursePublished(UUID courseId, boolean published);

    void updateCourseMetadata(UUID courseId, String title, String description);

    void archiveCourse(UUID courseId, Instant archivedAt);

    boolean isCourseInstructor(UUID courseId, UUID userId);
}
