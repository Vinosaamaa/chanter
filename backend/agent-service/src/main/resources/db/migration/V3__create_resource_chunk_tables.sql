CREATE TABLE resource_chunks (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    course_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    content_text TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    file_name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_resource_chunks_resource_index UNIQUE (resource_id, chunk_index),
    CONSTRAINT chk_resource_chunks_offsets CHECK (
        start_offset >= 0
        AND end_offset > start_offset
        AND chunk_index >= 0
    )
);

CREATE INDEX idx_resource_chunks_course_id ON resource_chunks (course_id);
CREATE INDEX idx_resource_chunks_resource_id ON resource_chunks (resource_id);
