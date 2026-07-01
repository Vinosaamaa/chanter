CREATE TABLE search_index_entries (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL,
    course_id UUID NOT NULL,
    course_title VARCHAR(255) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    title VARCHAR(512) NOT NULL,
    body_text VARCHAR(4000) NOT NULL,
    indexed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_search_index_study_server ON search_index_entries (study_server_id);
CREATE INDEX idx_search_index_course ON search_index_entries (course_id);
CREATE UNIQUE INDEX idx_search_index_source ON search_index_entries (document_type, source_id);
