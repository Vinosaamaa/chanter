CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    kind VARCHAR(64) NOT NULL,
    filter_bucket VARCHAR(32) NOT NULL,
    title VARCHAR(512) NOT NULL,
    body_preview VARCHAR(2000),
    course_label VARCHAR(255),
    href VARCHAR(1024) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    study_server_id UUID,
    course_id UUID,
    cohort_id UUID,
    channel_id UUID,
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP,
    done_at TIMESTAMP,
    CONSTRAINT uq_notifications_user_source_kind UNIQUE (user_id, source_type, source_id, kind)
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at);

CREATE INDEX idx_notifications_user_filter
    ON notifications (user_id, filter_bucket, created_at);
