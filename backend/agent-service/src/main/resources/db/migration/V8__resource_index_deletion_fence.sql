-- Keep deletion markers after content removal so a timed-out ingestion cannot recreate it.
CREATE TABLE resource_index_lifecycle (
    resource_id UUID PRIMARY KEY,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
