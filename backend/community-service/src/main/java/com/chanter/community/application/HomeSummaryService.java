package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CommunityAnnouncement;
import com.chanter.community.domain.CommunityAnnouncementStatus;
import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseLifecycle;
import com.chanter.community.domain.HomeSummary;
import com.chanter.community.domain.HomeSummaryAttentionItem;
import com.chanter.community.domain.HomeSummaryCourse;
import com.chanter.community.domain.HomeSummaryUpNextItem;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.StudyServerNavigation;
import com.chanter.community.domain.StudyServerNavigationCohort;
import com.chanter.community.domain.StudyServerNavigationCourse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeSummaryService {

    private static final String PROGRESS_UNAVAILABLE_REASON = "NO_CURRICULUM";
    private static final int MAX_ATTENTION = 3;
    private static final int MAX_COURSES = 4;
    private static final int MAX_UP_NEXT = 8;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(ZoneOffset.UTC);

    private final StudyServerNavigationService navigationService;
    private final CourseRepository courseRepository;
    private final OfficeHoursRepository officeHoursRepository;
    private final CommunityEventRepository communityEventRepository;
    private final CommunityAnnouncementRepository communityAnnouncementRepository;
    private final AuthUserDirectoryClient authUserDirectoryClient;
    private final Clock clock;

    public HomeSummaryService(
            StudyServerNavigationService navigationService,
            CourseRepository courseRepository,
            OfficeHoursRepository officeHoursRepository,
            CommunityEventRepository communityEventRepository,
            CommunityAnnouncementRepository communityAnnouncementRepository,
            AuthUserDirectoryClient authUserDirectoryClient,
            Clock clock
    ) {
        this.navigationService = navigationService;
        this.courseRepository = courseRepository;
        this.officeHoursRepository = officeHoursRepository;
        this.communityEventRepository = communityEventRepository;
        this.communityAnnouncementRepository = communityAnnouncementRepository;
        this.authUserDirectoryClient = authUserDirectoryClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeSummary buildHomeSummary(UUID userId) {
        Instant now = clock.instant();
        List<AccessibleStudyServer> servers = navigationService.listAccessibleStudyServers(userId);

        List<HomeSummaryCourse> courses = new ArrayList<>();
        List<HomeSummaryAttentionItem> attentionCandidates = new ArrayList<>();
        List<HomeSummaryUpNextItem> upNextCandidates = new ArrayList<>();
        Map<UUID, String> instructorNames = new HashMap<>();

        for (AccessibleStudyServer server : servers) {
            StudyServerNavigation navigation = navigationService.findNavigation(server.id(), userId);
            collectPublishedAnnouncementAttention(server.id(), userId, navigation.studyServerName(), attentionCandidates);

            for (StudyServerNavigationCourse course : navigation.courses()) {
                StudyServerNavigationCohort primaryCohort = course.cohorts().isEmpty()
                        ? null
                        : course.cohorts().getFirst();
                String instructorName = resolveInstructorDisplayName(course.id(), instructorNames);
                if (courses.size() < MAX_COURSES) {
                    courses.add(toCourseCard(server.id(), course, primaryCohort, instructorName));
                }

                for (StudyServerNavigationCohort cohort : course.cohorts()) {
                    collectOfficeHours(
                            server.id(),
                            course,
                            cohort,
                            now,
                            attentionCandidates,
                            upNextCandidates
                    );
                    collectStudyRoomPresence(
                            server.id(),
                            course,
                            cohort,
                            now,
                            upNextCandidates
                    );
                }
            }

            collectUpcomingEvents(server.id(), userId, now, upNextCandidates);
        }

        List<HomeSummaryAttentionItem> attention = attentionCandidates.stream()
                .sorted(attentionComparator())
                .limit(MAX_ATTENTION)
                .toList();
        List<HomeSummaryUpNextItem> upNext = upNextCandidates.stream()
                .sorted(upNextComparator())
                .limit(MAX_UP_NEXT)
                .toList();

        return new HomeSummary(courses, attention, upNext, List.of());
    }

    private HomeSummaryCourse toCourseCard(
            UUID studyServerId,
            StudyServerNavigationCourse course,
            StudyServerNavigationCohort cohort,
            String instructorDisplayName
    ) {
        UUID cohortId = cohort == null ? null : cohort.id();
        String cohortName = cohort == null ? null : cohort.name();
        String href = cohortId == null
                ? "/app/servers/" + studyServerId + "/courses/" + course.id() + "/overview"
                : "/app/servers/" + studyServerId + "/courses/" + course.id()
                        + "/overview?cohort=" + cohortId;
        return new HomeSummaryCourse(
                course.id(),
                studyServerId,
                course.title(),
                cohortId,
                cohortName,
                instructorDisplayName,
                null,
                PROGRESS_UNAVAILABLE_REASON,
                href
        );
    }

    private void collectOfficeHours(
            UUID studyServerId,
            StudyServerNavigationCourse course,
            StudyServerNavigationCohort cohort,
            Instant now,
            List<HomeSummaryAttentionItem> attention,
            List<HomeSummaryUpNextItem> upNext
    ) {
        List<OfficeHoursSession> sessions = officeHoursRepository.findSessionsByCohortId(cohort.id());
        for (OfficeHoursSession session : sessions) {
            if (!isUpcomingOrLive(session, now)) {
                continue;
            }
            String courseLabel = shortCourseLabel(course.title());
            String href = "/app/servers/" + studyServerId
                    + "/courses/" + course.id()
                    + "/office-hours?cohort=" + cohort.id()
                    + "&session=" + session.id();
            boolean live = session.status() == OfficeHoursSessionStatus.LIVE;
            String timeLabel = live ? "Live now" : TIME_FORMAT.format(session.startsAt());
            attention.add(new HomeSummaryAttentionItem(
                    "oh-" + session.id(),
                    "OFFICE_HOURS",
                    live ? "Office hours live" : "Office hours",
                    " · " + courseLabel + " · " + timeLabel,
                    false,
                    "Join",
                    "button",
                    href,
                    session.startsAt()
            ));
            upNext.add(new HomeSummaryUpNextItem(
                    "oh-up-" + session.id(),
                    "OFFICE_HOURS",
                    timeLabel,
                    null,
                    "Office hours — " + courseLabel,
                    "Join",
                    href,
                    session.startsAt()
            ));
        }
    }

    private void collectStudyRoomPresence(
            UUID studyServerId,
            StudyServerNavigationCourse course,
            StudyServerNavigationCohort cohort,
            Instant now,
            List<HomeSummaryUpNextItem> upNext
    ) {
        CourseChannel voiceChannel = course.channels().stream()
                .filter(channel -> channel.kind() == ChannelKind.VOICE)
                .filter(channel -> channel.cohortId() == null || channel.cohortId().equals(cohort.id()))
                .findFirst()
                .orElse(null);
        if (voiceChannel == null) {
            return;
        }
        int presenceCount = courseRepository.findCourseVoicePresences(voiceChannel.id(), now).size();
        if (presenceCount <= 0) {
            return;
        }
        String href = "/app/servers/" + studyServerId
                + "/courses/" + course.id()
                + "/chat?cohort=" + cohort.id()
                + "&channel=" + voiceChannel.id();
        upNext.add(new HomeSummaryUpNextItem(
                "study-" + voiceChannel.id(),
                "STUDY_ROOM",
                "Study room live",
                " · " + presenceCount + (presenceCount == 1 ? " person" : " people"),
                shortCourseLabel(course.title()),
                "Join",
                href,
                now
        ));
    }

    private void collectUpcomingEvents(
            UUID studyServerId,
            UUID userId,
            Instant now,
            List<HomeSummaryUpNextItem> upNext
    ) {
        List<CommunityEvent> events = communityEventRepository.findVisibleEvents(
                studyServerId,
                userId,
                CommunityEventFilter.UPCOMING,
                now
        );
        for (CommunityEvent event : events) {
            String href = "/app/servers/" + studyServerId + "/community/events?event=" + event.id();
            upNext.add(new HomeSummaryUpNextItem(
                    "event-" + event.id(),
                    "EVENT",
                    event.title(),
                    null,
                    DATE_FORMAT.format(event.startsAt()),
                    "Open",
                    href,
                    event.startsAt()
            ));
        }
    }

    private void collectPublishedAnnouncementAttention(
            UUID studyServerId,
            UUID userId,
            String serverName,
            List<HomeSummaryAttentionItem> attention
    ) {
        List<CommunityAnnouncement> published = communityAnnouncementRepository.findByStudyServer(
                studyServerId,
                userId,
                CommunityAnnouncementStatus.PUBLISHED
        );
        if (published.isEmpty()) {
            return;
        }
        String href = "/app/servers/" + studyServerId + "/community/announcements";
        String suffix = published.size() == 1
                ? " · " + published.getFirst().title()
                : " · " + published.size() + " published · " + serverName;
        attention.add(new HomeSummaryAttentionItem(
                "announcements-" + studyServerId,
                "ANNOUNCEMENTS",
                "Announcements",
                suffix,
                true,
                "View",
                "link",
                href,
                published.getFirst().createdAt()
        ));
    }

    private String resolveInstructorDisplayName(UUID courseId, Map<UUID, String> cache) {
        if (cache.containsKey(courseId)) {
            return cache.get(courseId);
        }
        CourseLifecycle lifecycle = courseRepository.findCourseLifecycle(courseId).orElse(null);
        if (lifecycle == null || lifecycle.instructorUserId() == null) {
            cache.put(courseId, null);
            return null;
        }
        String displayName = authUserDirectoryClient.findByIds(List.of(lifecycle.instructorUserId())).stream()
                .map(AuthUserProfile::displayName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        cache.put(courseId, displayName);
        return displayName;
    }

    private static boolean isUpcomingOrLive(OfficeHoursSession session, Instant now) {
        if (session.status() == OfficeHoursSessionStatus.LIVE) {
            return true;
        }
        if (session.status() != OfficeHoursSessionStatus.SCHEDULED) {
            return false;
        }
        return !session.endsAt().isBefore(now);
    }

    private static String shortCourseLabel(String title) {
        if (title == null || title.isBlank()) {
            return "Course";
        }
        int separator = title.indexOf('—');
        if (separator < 0) {
            separator = title.indexOf('-');
        }
        if (separator > 0) {
            return title.substring(0, separator).trim();
        }
        return title.trim();
    }

    private static Comparator<HomeSummaryAttentionItem> attentionComparator() {
        return Comparator
                .comparing((HomeSummaryAttentionItem item) -> !"OFFICE_HOURS".equals(item.kind()))
                .thenComparing(item -> item.startsAt() == null ? Instant.MAX : item.startsAt());
    }

    private static Comparator<HomeSummaryUpNextItem> upNextComparator() {
        return Comparator.comparing(item -> item.startsAt() == null ? Instant.MAX : item.startsAt());
    }
}
