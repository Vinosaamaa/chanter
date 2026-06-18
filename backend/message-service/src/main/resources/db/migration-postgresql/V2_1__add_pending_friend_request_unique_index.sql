DELETE FROM friend_requests older
USING friend_requests newer
WHERE older.status = 'PENDING'
  AND newer.status = 'PENDING'
  AND older.id > newer.id
  AND LEAST(older.sender_user_id, older.recipient_user_id) = LEAST(newer.sender_user_id, newer.recipient_user_id)
  AND GREATEST(older.sender_user_id, older.recipient_user_id) = GREATEST(newer.sender_user_id, newer.recipient_user_id);

CREATE UNIQUE INDEX uq_friend_requests_pending_pair
    ON friend_requests (
        LEAST(sender_user_id, recipient_user_id),
        GREATEST(sender_user_id, recipient_user_id)
    )
    WHERE status = 'PENDING';
