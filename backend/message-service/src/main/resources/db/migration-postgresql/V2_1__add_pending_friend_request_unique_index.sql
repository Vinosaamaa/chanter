CREATE UNIQUE INDEX uq_friend_requests_pending_pair
    ON friend_requests (
        LEAST(sender_user_id, recipient_user_id),
        GREATEST(sender_user_id, recipient_user_id)
    )
    WHERE status = 'PENDING';
