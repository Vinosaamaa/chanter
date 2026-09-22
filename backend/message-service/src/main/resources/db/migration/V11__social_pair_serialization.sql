CREATE TABLE social_pair_locks (
    first_user_id UUID NOT NULL,
    second_user_id UUID NOT NULL,
    PRIMARY KEY (first_user_id, second_user_id),
    CONSTRAINT social_pair_distinct CHECK (first_user_id <> second_user_id)
);
