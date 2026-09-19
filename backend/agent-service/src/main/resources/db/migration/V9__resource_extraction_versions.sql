-- Preparation does not hold a database lock. Only the latest live generation may commit.
ALTER TABLE resource_index_lifecycle ADD COLUMN generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE resource_index_lifecycle ADD COLUMN course_id UUID;
ALTER TABLE resource_index_lifecycle ADD COLUMN source_sha256 VARCHAR(64);
ALTER TABLE resource_index_lifecycle ADD COLUMN parser_version VARCHAR(64);
ALTER TABLE resource_index_lifecycle ADD COLUMN file_name VARCHAR(512);
ALTER TABLE resource_index_lifecycle ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE resource_index_lifecycle ADD COLUMN signals VARCHAR(512) NOT NULL DEFAULT '';
UPDATE resource_index_lifecycle SET status='DELETED' WHERE deleted=TRUE;

ALTER TABLE resource_chunks ADD COLUMN locator_kind VARCHAR(32);
ALTER TABLE resource_chunks ADD COLUMN locator_number INTEGER;
ALTER TABLE resource_chunks ADD COLUMN locator_label VARCHAR(200);
ALTER TABLE resource_chunks ADD COLUMN source_sha256 VARCHAR(64);
ALTER TABLE resource_chunks ADD COLUMN parser_version VARCHAR(64);
