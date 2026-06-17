CREATE INDEX idx_friend_requests_sender_recipient_status
    ON friend_requests (sender_user_id, recipient_user_id, status);

CREATE INDEX idx_friend_requests_recipient_sender_status
    ON friend_requests (recipient_user_id, sender_user_id, status);

CREATE INDEX idx_direct_messages_conversation
    ON direct_messages (sender_user_id, recipient_user_id, sent_at);

CREATE INDEX idx_direct_messages_conversation_reverse
    ON direct_messages (recipient_user_id, sender_user_id, sent_at);
