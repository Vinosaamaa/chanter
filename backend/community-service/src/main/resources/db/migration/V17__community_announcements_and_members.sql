CREATE TABLE community_announcements (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT community_announcements_status_check
        CHECK (status IN ('PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX community_announcements_server_created_idx
    ON community_announcements (study_server_id, created_at DESC);

CREATE TABLE community_announcement_reactions (
    announcement_id UUID NOT NULL REFERENCES community_announcements(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (announcement_id, user_id, kind),
    CONSTRAINT community_announcement_reactions_kind_check
        CHECK (kind IN ('LIKE'))
);
