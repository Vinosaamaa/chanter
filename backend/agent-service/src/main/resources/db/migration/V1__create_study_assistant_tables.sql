CREATE TABLE study_assistant_installs (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL UNIQUE,
    installed_by_user_id UUID NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE study_assistant_grants (
    id UUID PRIMARY KEY,
    install_id UUID NOT NULL REFERENCES study_assistant_installs(id) ON DELETE CASCADE,
    grant_type VARCHAR(64) NOT NULL CHECK (grant_type IN (
        'STUDY_SERVER_CHANNEL',
        'COURSE_CHANNEL',
        'COURSE',
        'COHORT',
        'COURSE_RESOURCE'
    )),
    grant_target_id UUID NOT NULL,
    UNIQUE (install_id, grant_type, grant_target_id)
);

CREATE INDEX idx_study_assistant_grants_install_id
    ON study_assistant_grants (install_id);
