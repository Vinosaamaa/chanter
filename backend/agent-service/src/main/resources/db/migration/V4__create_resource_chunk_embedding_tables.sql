CREATE TABLE resource_chunk_embeddings (
    chunk_id UUID PRIMARY KEY REFERENCES resource_chunks(id) ON DELETE CASCADE,
    resource_id UUID NOT NULL,
    course_id UUID NOT NULL,
    model_id VARCHAR(64) NOT NULL,
    dimensions INTEGER NOT NULL CHECK (dimensions > 0),
    embedding BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_resource_chunk_embeddings_resource_id
    ON resource_chunk_embeddings (resource_id);

CREATE INDEX idx_resource_chunk_embeddings_course_id
    ON resource_chunk_embeddings (course_id);
