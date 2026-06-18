ALTER TABLE friend_requests
    ADD COLUMN pending_pair_key VARCHAR AS (
        CASE
            WHEN status = 'PENDING' THEN CONCAT(CAST(sender_user_id AS VARCHAR), ':', CAST(recipient_user_id AS VARCHAR))
        END
    );

CREATE UNIQUE INDEX uq_friend_requests_pending_pair ON friend_requests (pending_pair_key);
