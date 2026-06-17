CREATE TABLE voice_channel_presences (
    channel_id UUID NOT NULL REFERENCES study_server_channels(id) ON DELETE CASCADE,
    member_user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (channel_id, member_user_id)
);
