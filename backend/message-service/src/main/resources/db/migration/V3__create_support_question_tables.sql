CREATE TABLE channel_messages (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL,
    sender_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE support_questions (
    id UUID PRIMARY KEY,
    channel_message_id UUID NOT NULL REFERENCES channel_messages(id),
    channel_id UUID NOT NULL,
    sender_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT support_questions_idempotency UNIQUE (channel_id, sender_user_id, idempotency_key)
);

CREATE INDEX idx_support_questions_channel_status
    ON support_questions (channel_id, status, created_at);
