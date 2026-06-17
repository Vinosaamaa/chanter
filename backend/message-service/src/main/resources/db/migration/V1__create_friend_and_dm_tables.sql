CREATE TABLE friend_requests (
    id UUID PRIMARY KEY,
    sender_user_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT friend_requests_no_self CHECK (sender_user_id <> recipient_user_id)
);

CREATE TABLE direct_messages (
    id UUID PRIMARY KEY,
    sender_user_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE user_blocks (
    blocker_user_id UUID NOT NULL,
    blocked_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (blocker_user_id, blocked_user_id),
    CONSTRAINT user_blocks_no_self CHECK (blocker_user_id <> blocked_user_id)
);
