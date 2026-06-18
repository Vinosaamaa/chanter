ALTER TABLE friend_requests
    ADD COLUMN pending_pair_key VARCHAR AS (
        CASE
            WHEN status = 'PENDING' THEN CONCAT(
                CASE
                    WHEN sender_user_id < recipient_user_id THEN CAST(sender_user_id AS VARCHAR)
                    ELSE CAST(recipient_user_id AS VARCHAR)
                END,
                ':',
                CASE
                    WHEN sender_user_id < recipient_user_id THEN CAST(recipient_user_id AS VARCHAR)
                    ELSE CAST(sender_user_id AS VARCHAR)
                END
            )
        END
    );

CREATE UNIQUE INDEX uq_friend_requests_pending_pair ON friend_requests (pending_pair_key);
