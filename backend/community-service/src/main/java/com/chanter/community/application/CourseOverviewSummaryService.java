package com.chanter.community.application;

import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CourseChannel;
import com.chanter.community.domain.CourseLifecycle;
import com.chanter.community.domain.CourseOverviewItem;
import com.chanter.community.domain.CourseOverviewSummary;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.StudyServerNavigation;
import com.chanter.community.domain.StudyServerNavigationCohort;
import com.chanter.community.domain.StudyServerNavigationCourse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseOverviewSummaryService {

    private static final String PROGRESS_UNAVAILABLE_REASON = "NO_CURRICULUM";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE", Locale.US).withZone(ZoneOffset.UTC);

    private final StudyServerNavigationService navigationService;
    private final CourseRepository courseRepository;
    private final OfficeHoursRepository officeHoursRepository;
    private final Clock clock;

    public CourseOverviewSummaryService(
            StudyServerNavigationService navigationService,
            CourseRepository courseRepository,
            OfficeHoursRepository officeHoursRepository,
            Clock clock
    ) {
        this.navigationService = navigationService;
        this.courseRepository = courseRepository;
        this.officeHoursRepository = officeHoursRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CourseOverviewSummary buildOverviewSummary(UUID courseId, UUID cohortId, UUID userId) {
        CourseLifecycle lifecycle = courseRepository.findCourseLifecycle(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        StudyServerNavigation navigation = navigationService.findNavigation(lifecycle.studyServerId(), userId);
        StudyServerNavigationCourse course = navigation.courses().stream()
                .filter(candidate -> candidate.id().equals(courseId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Course Overview requires Course access"
                ));

        StudyServerNavigationCohort cohort = resolveCohort(course, cohortId);
        Instant now = clock.instant();
        Instant weekEnd = now.plus(7, ChronoUnit.DAYS);

        List<CourseOverviewItem> thisWeek = new ArrayList<>();
        List<CourseOverviewItem> upNext = new ArrayList<>();

        if (cohort != null) {
            List<OfficeHoursSession> sessions = officeHoursRepository.findSessionsByCohortId(cohort.id());
            for (OfficeHoursSession session : sessions) {
                if (!isUpcomingOrLive(session, now)) {
                    continue;
                }
                String href = "/app/servers/" + lifecycle.studyServerId()
                        + "/courses/" + courseId
                        + "/office-hours?cohort=" + cohort.id()
                        + "&session=" + session.id();
                boolean live = session.status() == OfficeHoursSessionStatus.LIVE;
                String timeLabel = live ? "Live now" : TIME_FORMAT.format(session.startsAt());
                String dayLabel = live ? "Today" : DAY_FORMAT.format(session.startsAt());
                CourseOverviewItem item = new CourseOverviewItem(
                        "oh-" + session.id(),
                        "OFFICE_HOURS",
                        "Office hours",
                        dayLabel + " · " + timeLabel,
                        "Open",
                        href,
                        session.startsAt()
                );
                if (!session.startsAt().isAfter(weekEnd) || live) {
                    thisWeek.add(item);
                }
                upNext.add(item);
            }

            CourseChannel voiceChannel = course.channels().stream()
                    .filter(channel -> channel.kind() == ChannelKind.VOICE)
                    .filter(channel -> channel.cohortId() == null || channel.cohortId().equals(cohort.id()))
                    .findFirst()
                    .orElse(null);
            if (voiceChannel != null) {
                int presenceCount = courseRepository.findCourseVoicePresences(voiceChannel.id(), now).size();
                String href = "/app/servers/" + lifecycle.studyServerId()
                        + "/courses/" + courseId
                        + "/chat?cohort=" + cohort.id()
                        + "&channel=" + voiceChannel.id();
                String detail = presenceCount > 0
                        ? presenceCount + (presenceCount == 1 ? " person in study room" : " people in study room")
                        : "Study room available";
                upNext.add(new CourseOverviewItem(
                        "study-" + voiceChannel.id(),
                        "STUDY_ROOM",
                        "Study room",
                        detail,
                        "Join",
                        href,
                        now
                ));
            }
        }

        thisWeek.sort(Comparator.comparing(item -> item.startsAt() == null ? Instant.MAX : item.startsAt()));
        upNext.sort(Comparator.comparing(item -> item.startsAt() == null ? Instant.MAX : item.startsAt()));

        return new CourseOverviewSummary(
                null,
                PROGRESS_UNAVAILABLE_REASON,
                thisWeek,
                List.of(),
                upNext,
                List.of()
        );
    }

    private static StudyServerNavigationCohort resolveCohort(
            StudyServerNavigationCourse course,
            UUID cohortId
    ) {
        if (course.cohorts().isEmpty()) {
            if (cohortId != null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
            }
            return null;
        }
        if (cohortId == null) {
            return course.cohorts().getFirst();
        }
        return course.cohorts().stream()
                .filter(cohort -> cohort.id().equals(cohortId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Cohort is not accessible for this Course"
                ));
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
}
