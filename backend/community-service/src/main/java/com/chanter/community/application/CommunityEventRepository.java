package com.chanter.community.application;

import com.chanter.community.domain.CommunityEvent;
import com.chanter.community.domain.CommunityEventFilter;
import com.chanter.community.domain.CommunityEventRsvpStatus;
import com.chanter.community.domain.CommunityEventStatus;
import com.chanter.community.domain.CommunityEventVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityEventRepository {

    CommunityEvent save(CommunityEvent event);

    CommunityEvent update(CommunityEvent event);

    Optional<CommunityEvent> findById(UUID eventId, UUID viewerUserId);

    List<CommunityEvent> findVisibleEvents(
            UUID studyServerId,
            UUID viewerUserId,
            CommunityEventFilter filter,
            Instant now
    );

    void setStatus(UUID eventId, CommunityEventStatus status, Instant updatedAt);

    void upsertRsvp(UUID eventId, UUID userId, CommunityEventRsvpStatus status, Instant updatedAt);

    boolean isCourseAccessible(UUID courseId, UUID userId);

    boolean isCohortAccessible(UUID cohortId, UUID userId);

    boolean courseBelongsToStudyServer(UUID courseId, UUID studyServerId);

    boolean cohortBelongsToCourse(UUID cohortId, UUID courseId);
}
