CREATE TABLE office_hours_participants (
    session_id UUID NOT NULL REFERENCES office_hours_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    can_speak BOOLEAN NOT NULL DEFAULT FALSE,
    hand_raised BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (session_id, user_id)
);

CREATE INDEX office_hours_participants_active_idx
    ON office_hours_participants (session_id, active, joined_at);
