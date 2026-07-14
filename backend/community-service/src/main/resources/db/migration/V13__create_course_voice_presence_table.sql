CREATE TABLE course_voice_channel_presences (
    channel_id UUID NOT NULL REFERENCES course_channels(id) ON DELETE CASCADE,
    member_user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (channel_id, member_user_id)
);

CREATE INDEX idx_course_voice_channel_presences_channel
    ON course_voice_channel_presences (channel_id, expires_at, joined_at);
