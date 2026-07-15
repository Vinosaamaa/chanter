ALTER TABLE study_servers ADD COLUMN description VARCHAR(2000);
ALTER TABLE study_servers ADD COLUMN server_type VARCHAR(32);

ALTER TABLE study_servers
    ADD CONSTRAINT chk_study_servers_server_type
    CHECK (server_type IS NULL OR server_type IN ('SCHOOL', 'PROGRAM', 'PERSONAL'));

CREATE TABLE study_server_invitations (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    invited_by_user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT study_server_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT study_server_invitations_server_user_unique
        UNIQUE (study_server_id, invited_user_id)
);

CREATE INDEX study_server_invitations_pending_idx
    ON study_server_invitations (study_server_id, status, created_at);

ALTER TABLE courses ADD COLUMN description VARCHAR(2000);
ALTER TABLE courses ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE courses ALTER COLUMN published SET DEFAULT FALSE;
