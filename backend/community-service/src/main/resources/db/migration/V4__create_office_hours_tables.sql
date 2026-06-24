CREATE TABLE office_hours_sessions (
    id UUID PRIMARY KEY,
    cohort_id UUID NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    voice_channel_id UUID NOT NULL REFERENCES study_server_channels(id),
    scheduled_by_user_id UUID NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE INDEX office_hours_sessions_cohort_id_idx ON office_hours_sessions (cohort_id);

CREATE TABLE office_hours_waitlist_entries (
    session_id UUID NOT NULL REFERENCES office_hours_sessions(id) ON DELETE CASCADE,
    learner_user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (session_id, learner_user_id)
);
