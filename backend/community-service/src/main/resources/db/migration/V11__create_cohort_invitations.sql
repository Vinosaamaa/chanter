CREATE TABLE cohort_invitations (
    id UUID PRIMARY KEY,
    cohort_id UUID NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    invited_by_user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT cohort_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT cohort_invitations_cohort_user_unique
        UNIQUE (cohort_id, invited_user_id)
);

CREATE INDEX cohort_invitations_pending_idx
    ON cohort_invitations (cohort_id, status, created_at);
