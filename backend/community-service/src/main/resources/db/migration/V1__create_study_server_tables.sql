CREATE TABLE study_servers (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    owner_user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE study_server_roles (
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (study_server_id, user_id, role)
);

CREATE TABLE study_server_channels (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE (study_server_id, name)
);
