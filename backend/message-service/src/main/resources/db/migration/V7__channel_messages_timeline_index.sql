CREATE INDEX idx_channel_messages_channel_created_at
    ON channel_messages (channel_id, created_at);
