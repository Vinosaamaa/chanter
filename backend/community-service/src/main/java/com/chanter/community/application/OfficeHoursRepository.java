package com.chanter.community.application;

import com.chanter.community.domain.OfficeHoursSession;
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
}
