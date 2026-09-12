ALTER TABLE course_resources ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE course_resources ADD COLUMN sha256 VARCHAR(64);
ALTER TABLE course_resources ADD COLUMN idempotency_key UUID;
ALTER TABLE course_resources ADD COLUMN storage_backend VARCHAR(16) NOT NULL DEFAULT 'legacy';
ALTER TABLE course_resources ADD COLUMN migration_key VARCHAR(512);
ALTER TABLE course_resources ADD COLUMN ingestion_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE course_resources ADD COLUMN byte_reservation BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE course_resources ADD COLUMN lease_id UUID;
ALTER TABLE course_resources ADD COLUMN lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE course_resources ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE course_resources ADD COLUMN retry_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE course_resources ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
UPDATE course_resources SET updated_at = created_at;
CREATE UNIQUE INDEX uq_resource_upload_idempotency ON course_resources(uploaded_by_user_id, idempotency_key);
CREATE INDEX idx_resource_processing ON course_resources(state, retry_at, lease_until);

CREATE TABLE media_storage_budget (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    reserved_bytes BIGINT NOT NULL CHECK (reserved_bytes >= 0),
    request_month VARCHAR(7) NOT NULL,
    foreground_requests INTEGER NOT NULL,
    maintenance_requests INTEGER NOT NULL
);
INSERT INTO media_storage_budget (id, reserved_bytes, request_month, foreground_requests, maintenance_requests)
SELECT 1, COALESCE(SUM(byte_size), 0), '1970-01', 0, 0 FROM course_resources;
