CREATE TABLE course_resources (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    byte_size BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    ai_approved BOOLEAN NOT NULL,
    uploaded_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_course_resources_course_id_created_at
    ON course_resources (course_id, created_at DESC);
