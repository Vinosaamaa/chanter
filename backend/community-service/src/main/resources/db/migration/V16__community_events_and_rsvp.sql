CREATE TABLE community_events (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000),
    location VARCHAR(240),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER,
    visibility VARCHAR(16) NOT NULL,
    course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
    cohort_id UUID REFERENCES cohorts(id) ON DELETE SET NULL,
    created_by_user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT community_events_ends_after_starts CHECK (ends_at > starts_at),
    CONSTRAINT community_events_visibility_check
        CHECK (visibility IN ('HUB', 'COURSE', 'COHORT')),
    CONSTRAINT community_events_status_check
        CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT community_events_capacity_check
        CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT community_events_course_visibility_check
        CHECK (
            (visibility = 'HUB' AND course_id IS NULL AND cohort_id IS NULL)
            OR (visibility = 'COURSE' AND course_id IS NOT NULL AND cohort_id IS NULL)
            OR (visibility = 'COHORT' AND course_id IS NOT NULL AND cohort_id IS NOT NULL)
        )
);

CREATE INDEX community_events_server_starts_idx
    ON community_events (study_server_id, starts_at);

CREATE INDEX community_events_server_status_idx
    ON community_events (study_server_id, status);

CREATE TABLE community_event_rsvps (
    event_id UUID NOT NULL REFERENCES community_events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, user_id),
    CONSTRAINT community_event_rsvps_status_check
        CHECK (status IN ('GOING', 'INTERESTED', 'NOT_GOING'))
);

CREATE INDEX community_event_rsvps_user_idx
    ON community_event_rsvps (user_id, status);
