package com.chanter.community.application;

import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.Cohort;
import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.CohortTaQueueAccess;
import com.chanter.community.domain.CohortEnrollment;
import com.chanter.community.domain.CohortEnrollmentList;
import com.chanter.community.domain.CohortInvitation;
import com.chanter.community.domain.CohortInvitationDetails;
import com.chanter.community.domain.CohortInvitationStatus;
import com.chanter.community.domain.CohortJoinDetails;
import com.chanter.community.domain.CohortRoster;
import com.chanter.community.domain.CohortRosterMember;
import com.chanter.community.domain.Course;
import com.chanter.community.domain.CourseCatalog;
import com.chanter.community.domain.CourseCatalogFilter;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseChannelMessageAccess;
import com.chanter.community.domain.CourseLifecycle;
import com.chanter.community.domain.CourseResourceAccess;
import com.chanter.community.domain.CourseRole;
import com.chanter.community.domain.InstructorRole;
import com.chanter.community.domain.SupportQuestionChannelAccess;
import com.chanter.community.domain.VoiceMediaToken;
import com.chanter.community.domain.VoicePresence;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {

    public static final int MAX_COHORT_ENROLLMENT_PAGE_SIZE = 500;
    public static final int DEFAULT_COHORT_ENROLLMENT_PAGE_SIZE = 50;
    public static final int MAX_COHORT_ENROLLMENT_OFFSET = 10_000;
    private static final Duration COURSE_VOICE_PRESENCE_TTL = Duration.ofSeconds(30);

    private final CourseRepository courseRepository;
    private final AuthUserDirectoryClient authUserDirectoryClient;
    private final LiveKitTokenIssuer liveKitTokenIssuer;
    private final Clock clock;

    public CourseService(
            CourseRepository courseRepository,
            AuthUserDirectoryClient authUserDirectoryClient,
            LiveKitTokenIssuer liveKitTokenIssuer,
            Clock clock
    ) {
        this.courseRepository = courseRepository;
        this.authUserDirectoryClient = authUserDirectoryClient;
        this.liveKitTokenIssuer = liveKitTokenIssuer;
        this.clock = clock;
    }

    public CourseLifecycle createCourse(
            UUID studyServerId,
            UUID ownerUserId,
            String title,
            String description,
            String cohortName
    ) {
        requireStudyServerOwner(studyServerId, ownerUserId);

        String normalizedTitle = title.trim();
        String normalizedDescription = description == null || description.isBlank() ? null : description.trim();

        if (cohortName == null || cohortName.isBlank()) {
            return courseRepository.saveDraftCourse(
                    studyServerId,
                    normalizedTitle,
                    normalizedDescription,
                    ownerUserId,
                    clock.instant()
            );
        }

        UUID courseId = UUID.randomUUID();
        Cohort cohort = new Cohort(UUID.randomUUID(), courseId, cohortName.trim(), UUID.randomUUID());
        Course course = new Course(
                courseId,
                studyServerId,
                normalizedTitle,
                new InstructorRole(ownerUserId, CourseRole.INSTRUCTOR),
                cohort,
                defaultChannelsForCohort(courseId, cohort.id()),
                clock.instant()
        );

        courseRepository.save(course, normalizedDescription);
        return courseRepository.findCourseLifecycle(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Course was created but could not be loaded"
                ));
    }

    public Course createCourseWithCohort(
            UUID studyServerId,
            UUID ownerUserId,
            String title,
            UUID instructorUserId,
            String cohortName
    ) {
        CourseLifecycle lifecycle = createCourse(studyServerId, ownerUserId, title, null, cohortName);
        if (!instructorUserId.equals(ownerUserId)) {
            assignCourseInstructor(lifecycle.id(), ownerUserId, instructorUserId, null);
        }
        return toLegacyCourse(lifecycle);
    }

    @Transactional
    public Cohort addCohortToCourse(UUID courseId, UUID ownerUserId, String name) {
        UUID studyServerId = courseRepository.findStudyServerIdByCourseId(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireStudyServerOwner(studyServerId, ownerUserId);

        CourseLifecycle lifecycle = courseRepository.findCourseLifecycle(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        if (lifecycle.cohort().isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course already has a cohort");
        }

        String normalizedName = name.trim();
        UUID cohortId = UUID.randomUUID();
        Cohort cohort = new Cohort(cohortId, courseId, normalizedName, UUID.randomUUID());
        return courseRepository.addCohortToCourse(
                courseId,
                cohort,
                defaultChannelsForCohort(courseId, cohortId)
        );
    }

    @Transactional
    public void assignCourseInstructor(
            UUID courseId,
            UUID ownerUserId,
            UUID instructorUserId,
            String instructorEmail
    ) {
        UUID studyServerId = courseRepository.findStudyServerIdByCourseId(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireStudyServerOwner(studyServerId, ownerUserId);
        UUID resolvedInstructorUserId = resolveInstructorUserId(instructorUserId, instructorEmail);
        courseRepository.assignCourseInstructor(courseId, resolvedInstructorUserId);
    }

    public CourseLifecycle updateCourseMetadata(
            UUID courseId,
            UUID ownerUserId,
            String title,
            String description
    ) {
        requireCourseOwner(courseId, ownerUserId);
        String normalizedTitle = title.trim();
        String normalizedDescription = description == null || description.isBlank() ? null : description.trim();
        courseRepository.updateCourseMetadata(courseId, normalizedTitle, normalizedDescription);
        return courseRepository.findCourseLifecycle(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    public void publishCourse(UUID courseId, UUID ownerUserId) {
        requireCourseOwner(courseId, ownerUserId);
        courseRepository.setCoursePublished(courseId, true);
    }

    public void unpublishCourse(UUID courseId, UUID ownerUserId) {
        requireCourseOwner(courseId, ownerUserId);
        courseRepository.setCoursePublished(courseId, false);
    }

    public void archiveCourse(UUID courseId, UUID ownerUserId) {
        requireCourseOwner(courseId, ownerUserId);
        courseRepository.archiveCourse(courseId, clock.instant());
    }

    public CourseLifecycle getCourseLifecycle(UUID courseId, UUID viewerUserId) {
        CourseLifecycle lifecycle = courseRepository.findCourseLifecycle(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        boolean owner = courseRepository.isStudyServerOwner(lifecycle.studyServerId(), viewerUserId);
        boolean instructor = courseRepository.isCourseInstructor(courseId, viewerUserId);
        if (!owner && !instructor) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the Study Server owner or Course Instructor can view course lifecycle"
            );
        }
        return lifecycle;
    }

    private void requireStudyServerOwner(UUID studyServerId, UUID userId) {
        if (!courseRepository.isStudyServerOwner(studyServerId, userId)) {
            if (!courseRepository.studyServerExists(studyServerId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Study Server Owner can create Courses");
        }
    }

    private void requireCourseOwner(UUID courseId, UUID ownerUserId) {
        UUID studyServerId = courseRepository.findStudyServerIdByCourseId(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireStudyServerOwner(studyServerId, ownerUserId);
    }

    private static List<CourseChannel> defaultChannelsForCohort(UUID courseId, UUID cohortId) {
        return List.of(
                new CourseChannel(UUID.randomUUID(), courseId, cohortId, "announcements", ChannelKind.TEXT, 0),
                new CourseChannel(UUID.randomUUID(), courseId, cohortId, "questions", ChannelKind.TEXT, 1),
                new CourseChannel(UUID.randomUUID(), courseId, cohortId, "resources", ChannelKind.TEXT, 2),
                new CourseChannel(UUID.randomUUID(), courseId, cohortId, "study-room", ChannelKind.VOICE, 3)
        );
    }

    private static Course toLegacyCourse(CourseLifecycle lifecycle) {
        Cohort cohort = lifecycle.cohort()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Coupled course create requires a cohort"
                ));
        return new Course(
                lifecycle.id(),
                lifecycle.studyServerId(),
                lifecycle.title(),
                new InstructorRole(lifecycle.instructorUserId(), CourseRole.INSTRUCTOR),
                cohort,
                lifecycle.channels(),
                lifecycle.createdAt()
        );
    }

    public CourseCatalog findCourseCatalog(
            UUID studyServerId,
            UUID viewerUserId,
            String search,
            CourseCatalogFilter filter
    ) {
        boolean member = courseRepository.listAccessibleStudyServers(viewerUserId).stream()
                .anyMatch(server -> server.id().equals(studyServerId));
        if (!member) {
            if (!courseRepository.studyServerExists(studyServerId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course discovery requires Study Server membership");
        }
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (normalizedSearch.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course search must be 160 characters or fewer");
        }
        return new CourseCatalog(courseRepository.findPublishedCourseCatalog(
                studyServerId,
                viewerUserId,
                "%" + normalizedSearch + "%",
                filter
        ));
    }

    @Transactional
    public CourseChannel createCohortChannel(
            UUID cohortId,
            UUID actorUserId,
            String name,
            ChannelKind kind
    ) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can create channels"
        );
        UUID courseId = courseRepository.findCourseIdByCohortId(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found"));
        String normalizedName = normalizeChannelName(name);
        courseRepository.lockCohortForChannelMutation(cohortId);
        if (courseRepository.activeChannelNameExists(cohortId, normalizedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active channel already uses that name");
        }
        CourseChannel channel = new CourseChannel(
                UUID.randomUUID(),
                courseId,
                cohortId,
                normalizedName,
                kind,
                courseRepository.findNextChannelPosition(cohortId)
        );
        return courseRepository.saveChannel(channel);
    }

    @Transactional
    public CourseChannel renameCohortChannel(UUID channelId, UUID actorUserId, String name) {
        CourseChannel channel = requireManagedActiveChannel(channelId, actorUserId, "rename");
        String normalizedName = normalizeChannelName(name);
        courseRepository.lockCohortForChannelMutation(channel.cohortId());
        channel = requireManagedActiveChannel(channelId, actorUserId, "rename");
        if (courseRepository.activeChannelNameExistsExcluding(channel.cohortId(), normalizedName, channelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active channel already uses that name");
        }
        courseRepository.renameChannel(channelId, normalizedName);
        return new CourseChannel(
                channel.id(),
                channel.courseId(),
                channel.cohortId(),
                normalizedName,
                channel.kind(),
                channel.position()
        );
    }

    @Transactional
    public void archiveCohortChannel(UUID channelId, UUID actorUserId) {
        CourseChannel channel = requireManagedActiveChannel(channelId, actorUserId, "archive");
        courseRepository.lockCohortForChannelMutation(channel.cohortId());
        requireManagedActiveChannel(channelId, actorUserId, "archive");
        courseRepository.archiveChannel(channelId, clock.instant());
    }

    public VoicePresence joinCourseVoiceChannel(UUID channelId, UUID memberUserId) {
        requireAccessibleVoiceChannel(channelId, memberUserId);
        var joinedAt = clock.instant();
        return courseRepository.saveCourseVoicePresence(
                channelId,
                memberUserId,
                joinedAt,
                joinedAt.plus(COURSE_VOICE_PRESENCE_TTL)
        );
    }

    public List<VoicePresence> findCourseVoicePresences(UUID channelId, UUID viewerUserId) {
        requireAccessibleVoiceChannel(channelId, viewerUserId);
        return courseRepository.findCourseVoicePresences(channelId, clock.instant());
    }

    public void leaveCourseVoiceChannel(UUID channelId, UUID memberUserId) {
        CourseChannel channel = courseRepository.findActiveChannelById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found"));
        if (channel.kind() != ChannelKind.VOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Channel is not a Voice Channel");
        }
        courseRepository.deleteCourseVoicePresence(channelId, memberUserId);
    }

    public VoiceMediaToken issueCourseVoiceChannelMediaToken(UUID channelId, UUID memberUserId) {
        CourseChannel channel = requireAccessibleVoiceChannel(channelId, memberUserId);
        return liveKitTokenIssuer.issueForVoiceChannel(
                channel.id(),
                memberUserId,
                true,
                true
        );
    }

    public void enrollLearner(UUID cohortId, UUID instructorUserId, UUID learnerUserId) {
        requireCohortPeopleManager(
                cohortId,
                instructorUserId,
                "Only a Course Instructor or Study Server Owner can enroll learners"
        );
        courseRepository.enrollLearner(cohortId, learnerUserId, instructorUserId, clock.instant());
    }

    public void enrollLearnerByIdentity(
            UUID cohortId,
            UUID instructorUserId,
            String email,
            UUID learnerUserId
    ) {
        requireCohortPeopleManager(
                cohortId,
                instructorUserId,
                "Only a Course Instructor or Study Server Owner can enroll learners"
        );
        UUID resolvedLearnerUserId;
        if (email != null && !email.isBlank()) {
            AuthUserProfile learner = authUserDirectoryClient.findByEmail(normalizeEmail(email))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User email not found"));
            resolvedLearnerUserId = learner.userId();
        } else {
            if (learnerUserId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Email or learner user ID is required"
                );
            }
            resolvedLearnerUserId = learnerUserId;
        }
        courseRepository.enrollLearner(
                cohortId,
                resolvedLearnerUserId,
                instructorUserId,
                clock.instant()
        );
    }

    public CohortRoster getCohortRoster(UUID cohortId, UUID viewerUserId) {
        requireCohortRosterViewer(cohortId, viewerUserId);
        boolean canManagePeople = courseRepository.cohortHasPeopleManager(cohortId, viewerUserId);
        UUID instructorUserId = courseRepository.findCohortInstructorUserId(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found"));
        CohortEnrollmentList enrollmentList = courseRepository.listCohortEnrollments(
                cohortId,
                MAX_COHORT_ENROLLMENT_PAGE_SIZE,
                0,
                null
        );
        List<UUID> teachingAssistantUserIds = courseRepository.findTeachingAssistantUserIds(cohortId);
        List<CohortInvitation> pendingInvitations = canManagePeople
                ? courseRepository.findPendingInvitations(cohortId)
                : List.of();
        List<UUID> profileIds = java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of(instructorUserId),
                                        teachingAssistantUserIds.stream()
                                ),
                                enrollmentList.enrollments().stream().map(CohortEnrollment::learnerUserId)
                        ),
                        pendingInvitations.stream().map(CohortInvitation::invitedUserId)
                )
                .distinct()
                .toList();
        Map<UUID, AuthUserProfile> profilesById = new LinkedHashMap<>();
        authUserDirectoryClient.findByIds(profileIds)
                .forEach(profile -> profilesById.put(profile.userId(), profile));

        CohortRosterMember instructor = rosterMember(
                profilesById.get(instructorUserId),
                instructorUserId,
                "INSTRUCTOR",
                null,
                canManagePeople
        );
        List<CohortRosterMember> teachingAssistants = teachingAssistantUserIds.stream()
                .map(userId -> rosterMember(profilesById.get(userId), userId, "TA", null, canManagePeople))
                .toList();
        List<CohortRosterMember> enrolledLearners = enrollmentList.enrollments().stream()
                .filter(enrollment -> !teachingAssistantUserIds.contains(enrollment.learnerUserId()))
                .map(enrollment -> rosterMember(
                        profilesById.get(enrollment.learnerUserId()),
                        enrollment.learnerUserId(),
                        "LEARNER",
                        enrollment.enrolledAt(),
                        enrollment.assignedTeachingAssistantUserId(),
                        canManagePeople
                ))
                .toList();
        List<CohortRosterMember> pendingLearners = pendingInvitations.stream()
                .map(invitation -> pendingRosterMember(invitation, profilesById.get(invitation.invitedUserId())))
                .toList();
        List<CohortRosterMember> learners = java.util.stream.Stream.concat(
                        pendingLearners.stream(),
                        enrolledLearners.stream()
                )
                .toList();
        return new CohortRoster(
                cohortId,
                instructor,
                teachingAssistants,
                learners,
                enrolledLearners.size(),
                teachingAssistants.size(),
                pendingLearners.size(),
                MAX_COHORT_ENROLLMENT_PAGE_SIZE,
                0
        );
    }

    public void addTeachingAssistant(UUID cohortId, UUID actorUserId, UUID userId) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can manage teaching assistants"
        );
        if (authUserDirectoryClient.findByIds(List.of(userId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        courseRepository.addTeachingAssistant(cohortId, userId);
    }

    public void removeTeachingAssistant(UUID cohortId, UUID actorUserId, UUID userId) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can manage teaching assistants"
        );
        courseRepository.removeTeachingAssistant(cohortId, userId);
    }

    public void assignTeachingAssistant(
            UUID cohortId,
            UUID actorUserId,
            List<UUID> learnerUserIds,
            UUID teachingAssistantUserId
    ) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can assign teaching assistants"
        );
        List<UUID> distinctLearnerUserIds = learnerUserIds.stream().distinct().toList();
        if (teachingAssistantUserId != null
                && !courseRepository.cohortHasTeachingAssistant(cohortId, teachingAssistantUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teaching assistant is not assigned to this Cohort");
        }
        if (!courseRepository.cohortHasEnrollments(cohortId, distinctLearnerUserIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All selected learners must be enrolled in this Cohort");
        }
        courseRepository.assignTeachingAssistant(
                cohortId,
                distinctLearnerUserIds,
                teachingAssistantUserId
        );
    }

    public CohortInvitationDetails createCohortInvitation(
            UUID cohortId,
            UUID actorUserId,
            String email
    ) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can invite learners"
        );
        String normalizedEmail = normalizeEmail(email);
        AuthUserProfile invitedProfile = authUserDirectoryClient.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User email not found"));
        if (courseRepository.cohortHasEnrollments(cohortId, List.of(invitedProfile.userId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already enrolled in this Cohort");
        }
        CohortInvitation invitation = courseRepository.saveInvitation(new CohortInvitation(
                UUID.randomUUID(),
                cohortId,
                invitedProfile.userId(),
                normalizedEmail,
                actorUserId,
                CohortInvitationStatus.PENDING,
                clock.instant()
        ));
        return new CohortInvitationDetails(invitation, invitedProfile);
    }

    public void cancelCohortInvitation(UUID cohortId, UUID actorUserId, UUID invitationId) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can cancel invitations"
        );
        courseRepository.cancelInvitation(cohortId, invitationId, clock.instant());
    }

    public void removeEnrollment(UUID cohortId, UUID actorUserId, UUID learnerUserId) {
        requireCohortPeopleManager(
                cohortId,
                actorUserId,
                "Only a Course Instructor or Study Server Owner can remove learners"
        );
        courseRepository.removeEnrollment(cohortId, learnerUserId);
    }

    private static CohortRosterMember rosterMember(
            AuthUserProfile profile,
            UUID userId,
            String role,
            java.time.Instant enrolledAt,
            boolean includeEmail
    ) {
        return rosterMember(profile, userId, role, enrolledAt, null, includeEmail);
    }

    private static CohortRosterMember rosterMember(
            AuthUserProfile profile,
            UUID userId,
            String role,
            java.time.Instant enrolledAt,
            UUID assignedTeachingAssistantUserId,
            boolean includeEmail
    ) {
        String displayName = profile == null ? "Account unavailable" : profile.displayName();
        return new CohortRosterMember(
                userId,
                null,
                displayName,
                includeEmail && profile != null ? profile.email() : null,
                role,
                "ENROLLED",
                assignedTeachingAssistantUserId,
                enrolledAt
        );
    }

    private static CohortRosterMember pendingRosterMember(
            CohortInvitation invitation,
            AuthUserProfile profile
    ) {
        return new CohortRosterMember(
                invitation.invitedUserId(),
                invitation.id(),
                profile == null ? "Account unavailable" : profile.displayName(),
                invitation.email(),
                "LEARNER",
                "PENDING",
                null,
                invitation.createdAt()
        );
    }

    private void requireCohortRosterViewer(UUID cohortId, UUID viewerUserId) {
        if (courseRepository.cohortHasRosterViewer(cohortId, viewerUserId)) {
            return;
        }
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cohort roster requires Cohort membership");
    }

    private void requireCohortPeopleManager(UUID cohortId, UUID viewerUserId, String forbiddenMessage) {
        if (courseRepository.cohortHasPeopleManager(cohortId, viewerUserId)) {
            return;
        }
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
    }

    private CourseChannel requireManagedActiveChannel(UUID channelId, UUID actorUserId, String action) {
        CourseChannel channel = courseRepository.findActiveChannelById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found"));
        requireCohortPeopleManager(
                channel.cohortId(),
                actorUserId,
                "Only a Course Instructor or Study Server Owner can " + action + " channels"
        );
        return channel;
    }

    private CourseChannel requireAccessibleVoiceChannel(UUID channelId, UUID viewerUserId) {
        CourseChannel channel = findAccessibleChannel(channelId, viewerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Voice Channel access requires Cohort membership"
                ));
        if (channel.kind() != ChannelKind.VOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Channel is not a Voice Channel");
        }
        return channel;
    }

    private UUID resolveInstructorUserId(UUID instructorUserId, String instructorEmail) {
        if (instructorUserId != null) {
            if (authUserDirectoryClient.findByIds(List.of(instructorUserId)).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return instructorUserId;
        }
        if (instructorEmail == null || instructorEmail.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Instructor user id or email is required"
            );
        }
        return authUserDirectoryClient.findByEmail(normalizeEmail(instructorEmail))
                .map(AuthUserProfile::userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User email not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeChannelName(String name) {
        String normalizedName = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalizedName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Channel name must contain at least one letter or number"
            );
        }
        return normalizedName;
    }

    public void joinCohort(UUID cohortId, UUID learnerUserId, UUID inviteCode) {
        CohortJoinDetails joinDetails = courseRepository.findCohortJoinDetails(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found"));
        boolean validInvite = inviteCode != null && joinDetails.inviteCode().equals(inviteCode);
        switch (joinDetails.enrollmentPolicy()) {
            case OPEN -> {
                boolean member = courseRepository.listAccessibleStudyServers(learnerUserId).stream()
                        .anyMatch(server -> server.id().equals(joinDetails.studyServerId()));
                if (!member) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Open Cohort enrollment requires Study Server membership"
                    );
                }
            }
            case INVITE_ONLY -> {
                if (!validInvite) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid cohort invite code");
                }
            }
            case OPENING_SOON -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cohort enrollment is not open yet"
            );
            case CLOSED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Cohort enrollment is closed");
        }

        courseRepository.enrollLearner(cohortId, learnerUserId, learnerUserId, clock.instant());
    }

    public UUID getCohortInviteCode(UUID cohortId, UUID instructorUserId) {
        return courseRepository.findCohortInviteCodeForInstructor(cohortId, instructorUserId)
                .orElseThrow(() -> {
                    if (!courseRepository.cohortExists(cohortId)) {
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
                    }
                    return new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Only the Course Instructor can view invite details"
                    );
                });
    }

    public CohortEnrollmentList listCohortEnrollments(
            UUID cohortId,
            UUID instructorUserId,
            int limit,
            int offset,
            String learnerSearch
    ) {
        requireCohortPeopleManager(
                cohortId,
                instructorUserId,
                "Only a Course Instructor or Study Server Owner can view enrollments"
        );
        int boundedLimit = clampEnrollmentLimit(limit);
        int boundedOffset = Math.min(Math.max(offset, 0), MAX_COHORT_ENROLLMENT_OFFSET);
        String normalizedSearch = normalizeLearnerSearch(learnerSearch);
        return courseRepository.listCohortEnrollments(
                cohortId,
                boundedLimit,
                boundedOffset,
                normalizedSearch
        );
    }

    private void requireCohortInstructor(UUID cohortId, UUID instructorUserId, String forbiddenMessage) {
        if (courseRepository.cohortHasInstructor(cohortId, instructorUserId)) {
            return;
        }
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
    }

    private static int clampEnrollmentLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_COHORT_ENROLLMENT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_COHORT_ENROLLMENT_PAGE_SIZE);
    }

    private static String normalizeLearnerSearch(String learnerSearch) {
        if (learnerSearch == null) {
            return null;
        }
        String trimmed = learnerSearch.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    public Optional<CourseChannel> findAccessibleChannel(UUID channelId, UUID viewerUserId) {
        if (!courseRepository.courseChannelExists(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }

        return courseRepository.findAccessibleChannel(channelId, viewerUserId);
    }

    public SupportQuestionChannelAccess findSupportQuestionChannelAccess(UUID channelId, UUID userId) {
        if (!courseRepository.courseChannelExists(channelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Channel not found");
        }

        return courseRepository.findSupportQuestionChannelAccess(channelId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Channel access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CourseChannelMessageAccess findCourseChannelMessageAccess(UUID channelId, UUID userId) {
        CourseChannel channel = findAccessibleChannel(channelId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Channel access requires Cohort Enrollment or Instructor role"
                ));
        if (channel.kind() != ChannelKind.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Channel is not a Text Channel");
        }

        return new CourseChannelMessageAccess(
                channel.id(),
                channel.courseId(),
                channel.name(),
                true,
                true
        );
    }

    public CourseResourceAccess findCourseResourceAccess(UUID courseId, UUID userId) {
        if (!courseRepository.courseExists(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        return courseRepository.findCourseResourceAccess(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Resource access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CohortTaQueueAccess findCohortTaQueueAccess(UUID cohortId, UUID userId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }

        return courseRepository.findCohortTaQueueAccess(cohortId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "TA Queue access requires Cohort Enrollment or Instructor role"
                ));
    }

    public CohortOfficeHoursAccess findCohortOfficeHoursAccess(UUID cohortId, UUID userId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }

        return courseRepository.findCohortOfficeHoursAccess(cohortId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Office Hours access requires Cohort Enrollment or Instructor role"
                ));
    }
}
