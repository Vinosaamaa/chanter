CREATE UNIQUE INDEX uq_friend_requests_pending_pair
    ON friend_requests (sender_user_id, recipient_user_id, status);
