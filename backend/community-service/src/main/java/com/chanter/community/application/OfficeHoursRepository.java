package com.chanter.community.application;

import com.chanter.community.domain.OfficeHoursSession;
import com.chanter.community.domain.OfficeHoursParticipant;
import com.chanter.community.domain.OfficeHoursSessionStatus;
import com.chanter.community.domain.OfficeHoursWaitlistEntry;
import com.chanter.community.domain.OfficeHoursWaitlistStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeHoursRepository {

    OfficeHoursSession saveSession(OfficeHoursSession session);

    Optional<OfficeHoursSession> findSessionById(UUID sessionId);

    List<OfficeHoursSession> findSessionsByCohortId(UUID cohortId);

    OfficeHoursSession updateSessionStatus(UUID sessionId, OfficeHoursSessionStatus status);

    OfficeHoursSession updateSessionSchedule(UUID sessionId, Instant startsAt, Instant endsAt);

    OfficeHoursWaitlistEntry saveWaitlistEntry(OfficeHoursWaitlistEntry entry);

    Optional<OfficeHoursWaitlistEntry> findWaitlistEntry(UUID sessionId, UUID learnerUserId);

    List<OfficeHoursWaitlistEntry> findWaitlistEntries(UUID sessionId);

    OfficeHoursWaitlistEntry rejoinWaitlistEntry(
            UUID sessionId,
            UUID learnerUserId,
            Instant joinedAt,
            OfficeHoursWaitlistStatus status
    );

    Optional<OfficeHoursWaitlistEntry> claimNextWaitingEntry(UUID sessionId);

    Optional<UUID> findStudyServerIdForCohort(UUID cohortId);

    OfficeHoursParticipant saveParticipant(OfficeHoursParticipant participant);

    Optional<OfficeHoursParticipant> findParticipant(UUID sessionId, UUID userId);

    List<OfficeHoursParticipant> findActiveParticipants(UUID sessionId);

    OfficeHoursParticipant updateParticipantHand(UUID sessionId, UUID userId, boolean raised, Instant updatedAt);

    OfficeHoursParticipant updateParticipantSpeaking(
            UUID sessionId,
            UUID userId,
            boolean canSpeak,
            Instant updatedAt
    );

    void deactivateParticipant(UUID sessionId, UUID userId, Instant updatedAt);

    void deactivateParticipants(UUID sessionId, Instant updatedAt);
}
