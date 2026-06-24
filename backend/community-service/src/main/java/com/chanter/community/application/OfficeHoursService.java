package com.chanter.community.application;

import com.chanter.community.domain.CohortOfficeHoursAccess;
import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.OfficeHoursWaitlistStatus;
import com.chanter.community.domain.VoicePresence;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfficeHoursService {

    private final OfficeHoursRepository officeHoursRepository;
    private final CourseRepository courseRepository;
    private final StudyServerRepository studyServerRepository;
    private final Clock clock;

    public OfficeHoursService(
            OfficeHoursRepository officeHoursRepository,
            CourseRepository courseRepository,
            StudyServerRepository studyServerRepository,
            Clock clock
    ) {
        this.officeHoursRepository = officeHoursRepository;
        this.courseRepository = courseRepository;
        this.studyServerRepository = studyServerRepository;
        this.clock = clock;
    }

    @Transactional
    public OfficeHoursSession scheduleOfficeHours(
            UUID cohortId,
            UUID instructorUserId,
            Instant startsAt,
            Instant endsAt
    ) {
        CohortOfficeHoursAccess access = requireOfficeHoursAccess(cohortId, instructorUserId);
        if (!access.canScheduleOfficeHours()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can schedule Office Hours");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Office Hours end time must be after start time");
        }

        UUID studyServerId = officeHoursRepository.findStudyServerIdForCohort(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found"));
        UUID voiceChannelId = studyServerRepository.findDefaultVoiceChannelId(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Study Server has no Voice Channel for Office Hours"
                ));

        OfficeHoursSession session = new OfficeHoursSession(
                UUID.randomUUID(),
                cohortId,
                voiceChannelId,
                instructorUserId,
                startsAt,
                endsAt,
                OfficeHoursSessionStatus.SCHEDULED,
                clock.instant()
        );

        return officeHoursRepository.saveSession(session);
    }

    @Transactional(readOnly = true)
    public List<OfficeHoursSession> listOfficeHoursSessions(UUID cohortId, UUID viewerUserId) {
        requireOfficeHoursAccess(cohortId, viewerUserId);
        return officeHoursRepository.findSessionsByCohortId(cohortId);
    }

    @Transactional(readOnly = true)
    public OfficeHoursSession findOfficeHoursSession(UUID sessionId, UUID viewerUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        requireOfficeHoursAccess(session.cohortId(), viewerUserId);
        return session;
    }

    @Transactional
    public OfficeHoursWaitlistEntry joinWaitlist(UUID sessionId, UUID learnerUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        CohortOfficeHoursAccess access = requireOfficeHoursAccess(session.cohortId(), learnerUserId);
        if (!access.canJoinOfficeHours()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Office Hours join requires Cohort Enrollment");
        }
        requireOpenWindow(session);

        officeHoursRepository.findWaitlistEntry(sessionId, learnerUserId).ifPresent(existing -> {
            if (existing.status() == OfficeHoursWaitlistStatus.WAITING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Learner is already on the Office Hours waitlist");
            }
            if (existing.status() == OfficeHoursWaitlistStatus.ADMITTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Learner has already been admitted to Office Hours");
            }
        });

        markSessionLiveIfNeeded(session);

        OfficeHoursWaitlistEntry existingLeft = officeHoursRepository.findWaitlistEntry(sessionId, learnerUserId)
                .filter(entry -> entry.status() == OfficeHoursWaitlistStatus.LEFT)
                .orElse(null);
        if (existingLeft != null) {
            return officeHoursRepository.rejoinWaitlistEntry(
                    sessionId,
                    learnerUserId,
                    clock.instant(),
                    OfficeHoursWaitlistStatus.WAITING
            );
        }

        return officeHoursRepository.saveWaitlistEntry(new OfficeHoursWaitlistEntry(
                sessionId,
                learnerUserId,
                clock.instant(),
                OfficeHoursWaitlistStatus.WAITING
        ));
    }

    @Transactional
    public OfficeHoursWaitlistEntry admitNextLearner(UUID sessionId, UUID actorUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        requireManageAccess(session.cohortId(), actorUserId);
        requireOpenWindow(session);

        OfficeHoursWaitlistEntry admitted = officeHoursRepository.claimNextWaitingEntry(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No learners waiting for Office Hours"));

        studyServerRepository.saveVoicePresence(session.voiceChannelId(), admitted.learnerUserId());
        markSessionLiveIfNeeded(session);

        return admitted;
    }

    @Transactional
    public VoicePresence joinVoiceAsManager(UUID sessionId, UUID actorUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        requireManageAccess(session.cohortId(), actorUserId);
        requireOpenWindow(session);

        markSessionLiveIfNeeded(session);
        return studyServerRepository.saveVoicePresence(session.voiceChannelId(), actorUserId);
    }

    @Transactional
    public VoicePresence joinVoiceAsAdmittedLearner(UUID sessionId, UUID learnerUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        CohortOfficeHoursAccess access = requireOfficeHoursAccess(session.cohortId(), learnerUserId);
        if (!access.canJoinOfficeHours()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Office Hours voice join requires Cohort Enrollment");
        }
        requireOpenWindow(session);

        OfficeHoursWaitlistEntry entry = officeHoursRepository.findWaitlistEntry(sessionId, learnerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Learner must join the Office Hours waitlist first"
                ));
        if (entry.status() != OfficeHoursWaitlistStatus.ADMITTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Learner must be admitted before joining Office Hours voice");
        }

        markSessionLiveIfNeeded(session);
        return studyServerRepository.saveVoicePresence(session.voiceChannelId(), learnerUserId);
    }

    @Transactional(readOnly = true)
    public List<OfficeHoursWaitlistEntry> listWaitlist(UUID sessionId, UUID viewerUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        requireManageAccess(session.cohortId(), viewerUserId);
        return officeHoursRepository.findWaitlistEntries(sessionId);
    }

    @Transactional
    public OfficeHoursSession endSession(UUID sessionId, UUID actorUserId) {
        OfficeHoursSession session = requireSession(sessionId);
        requireManageAccess(session.cohortId(), actorUserId);

        if (session.status() == OfficeHoursSessionStatus.ENDED
                || session.status() == OfficeHoursSessionStatus.CANCELLED) {
            return session;
        }

        return officeHoursRepository.updateSessionStatus(sessionId, OfficeHoursSessionStatus.ENDED);
    }

    private OfficeHoursSession requireSession(UUID sessionId) {
        return officeHoursRepository.findSessionById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Office Hours session not found"));
    }

    private CohortOfficeHoursAccess requireOfficeHoursAccess(UUID cohortId, UUID userId) {
        if (!courseRepository.cohortExists(cohortId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort not found");
        }

        return courseRepository.findCohortOfficeHoursAccess(cohortId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Office Hours access requires Cohort Enrollment or Instructor role"
                ));
    }

    private void requireManageAccess(UUID cohortId, UUID userId) {
        CohortOfficeHoursAccess access = requireOfficeHoursAccess(cohortId, userId);
        if (!access.canManageOfficeHours()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Course Instructor can manage Office Hours");
        }
    }

    private void requireOpenWindow(OfficeHoursSession session) {
        if (session.status() == OfficeHoursSessionStatus.ENDED
                || session.status() == OfficeHoursSessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Office Hours session has ended");
        }

        Instant now = clock.instant();
        if (now.isBefore(session.startsAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Office Hours window has not opened yet");
        }
        if (!now.isBefore(session.endsAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Office Hours window has closed");
        }
    }

    private void markSessionLiveIfNeeded(OfficeHoursSession session) {
        if (session.status() == OfficeHoursSessionStatus.SCHEDULED) {
            officeHoursRepository.updateSessionStatus(session.id(), OfficeHoursSessionStatus.LIVE);
        }
    }
}
