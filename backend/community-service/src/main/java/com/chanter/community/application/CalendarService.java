package com.chanter.community.application;

import com.chanter.community.domain.AccessibleStudyServer;
import com.chanter.community.domain.CalendarAggregate;
import com.chanter.community.domain.CalendarItem;
import com.chanter.community.domain.CalendarItemType;
import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import com.chanter.community.domain.CommunityEventRsvpStatus;
import com.chanter.community.domain.CommunityEventStatus;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.StudyServerNavigation;
import com.chanter.community.domain.StudyServerNavigationCohort;
import com.chanter.community.domain.StudyServerNavigationCourse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    static final String DEADLINES_NOTE =
            "Deadlines are omitted: learning resources have no due-date field yet "
                    + "(createdAt alone is not a meaningful calendar date). "
                    + "Calendar primary sources are Office Hours and Community events.";

    private final StudyServerNavigationService navigationService;
    private final OfficeHoursRepository officeHoursRepository;
    private final CommunityEventRepository communityEventRepository;
    private final Clock clock;

    public CalendarService(
            StudyServerNavigationService navigationService,
            OfficeHoursRepository officeHoursRepository,
            CommunityEventRepository communityEventRepository,
            Clock clock
    ) {
        this.navigationService = navigationService;
        this.officeHoursRepository = officeHoursRepository;
        this.communityEventRepository = communityEventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarAggregate buildCalendar(
            UUID userId,
            Instant from,
            Instant to,
            String typesRaw,
            String search
    ) {
        Instant rangeFrom = from != null ? from : clock.instant().minusSeconds(30L * 24 * 3600);
        Instant rangeTo = to != null ? to : clock.instant().plusSeconds(60L * 24 * 3600);
        if (!rangeFrom.isBefore(rangeTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        Set<CalendarItemType> types = CalendarItemType.parseTypes(typesRaw);
        boolean goingOnly = CalendarItemType.includesGoingFilter(typesRaw);
        String searchNeedle = normalizeSearch(search);

        List<CalendarItem> items = new ArrayList<>();
        List<AccessibleStudyServer> servers = navigationService.listAccessibleStudyServers(userId);

        for (AccessibleStudyServer server : servers) {
            StudyServerNavigation navigation = navigationService.findNavigation(server.id(), userId);

            if (types.contains(CalendarItemType.OFFICE_HOURS) && !goingOnly) {
                collectOfficeHours(server.id(), navigation, rangeFrom, rangeTo, searchNeedle, items);
            }
            if (types.contains(CalendarItemType.EVENT) || goingOnly) {
                collectEvents(server.id(), server.name(), userId, rangeFrom, rangeTo, goingOnly, searchNeedle, items);
            }
        }

        items.sort(Comparator.comparing(CalendarItem::startsAt));

        List<String> notes = new ArrayList<>();
        if (types.contains(CalendarItemType.DEADLINE) || types.equals(EnumSet.allOf(CalendarItemType.class))) {
            notes.add(DEADLINES_NOTE);
        }

        return new CalendarAggregate(List.copyOf(items), List.copyOf(notes));
    }

    private void collectOfficeHours(
            UUID studyServerId,
            StudyServerNavigation navigation,
            Instant from,
            Instant to,
            String searchNeedle,
            List<CalendarItem> items
    ) {
        for (StudyServerNavigationCourse course : navigation.courses()) {
            for (StudyServerNavigationCohort cohort : course.cohorts()) {
                List<OfficeHoursSession> sessions = officeHoursRepository.findSessionsByCohortId(cohort.id());
                for (OfficeHoursSession session : sessions) {
                    if (!isVisibleOfficeHours(session)) {
                        continue;
                    }
                    if (!overlaps(session.startsAt(), session.endsAt(), from, to)) {
                        continue;
                    }
                    String courseLabel = shortCourseLabel(course.title());
                    String title = "Office hours";
                    if (!matchesSearch(searchNeedle, title, courseLabel, course.title())) {
                        continue;
                    }
                    String href = "/app/servers/" + studyServerId
                            + "/courses/" + course.id()
                            + "/office-hours?cohort=" + cohort.id()
                            + "&session=" + session.id();
                    items.add(new CalendarItem(
                            "oh-" + session.id(),
                            CalendarItemType.OFFICE_HOURS,
                            title,
                            courseLabel,
                            session.startsAt(),
                            session.endsAt(),
                            href,
                            "Join",
                            "JOIN",
                            null,
                            studyServerId,
                            course.id(),
                            cohort.id(),
                            session.id()
                    ));
                }
            }
        }
    }

    private void collectEvents(
            UUID studyServerId,
            String serverName,
            UUID userId,
            Instant from,
            Instant to,
            boolean goingOnly,
            String searchNeedle,
            List<CalendarItem> items
    ) {
        // Instant.EPOCH with UPCOMING yields all SCHEDULED visible events (past and future).
        List<CommunityEvent> events = communityEventRepository.findVisibleEvents(
                studyServerId,
                userId,
                CommunityEventFilter.UPCOMING,
                Instant.EPOCH
        );

        for (CommunityEvent event : events) {
            if (event.status() == CommunityEventStatus.CANCELLED) {
                continue;
            }
            if (!overlaps(event.startsAt(), event.endsAt(), from, to)) {
                continue;
            }
            if (goingOnly && event.viewerRsvp() != CommunityEventRsvpStatus.GOING) {
                continue;
            }
            String context = serverName == null || serverName.isBlank() ? "Community" : serverName;
            if (!matchesSearch(searchNeedle, event.title(), context, event.description(), event.location())) {
                continue;
            }
            String href = "/app/servers/" + studyServerId + "/community/events?event=" + event.id();
            items.add(new CalendarItem(
                    "event-" + event.id(),
                    CalendarItemType.EVENT,
                    event.title(),
                    context,
                    event.startsAt(),
                    event.endsAt(),
                    href,
                    "Going",
                    "RSVP",
                    event.viewerRsvp(),
                    studyServerId,
                    event.courseId(),
                    event.cohortId(),
                    event.id()
            ));
        }
    }

    private static boolean isVisibleOfficeHours(OfficeHoursSession session) {
        return session.status() == OfficeHoursSessionStatus.SCHEDULED
                || session.status() == OfficeHoursSessionStatus.LIVE
                || session.status() == OfficeHoursSessionStatus.ENDED;
    }

    private static boolean overlaps(Instant startsAt, Instant endsAt, Instant from, Instant to) {
        return startsAt.isBefore(to) && !endsAt.isBefore(from);
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSearch(String needle, String... fields) {
        if (needle == null) {
            return true;
        }
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
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
}
