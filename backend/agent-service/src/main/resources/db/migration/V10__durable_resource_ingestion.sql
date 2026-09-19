CREATE TABLE durable_consumer_lock (id INT PRIMARY KEY);
INSERT INTO durable_consumer_lock VALUES (1);
CREATE TABLE durable_event_cursor (
    producer VARCHAR(32) NOT NULL, aggregate_key VARCHAR(300) NOT NULL,
    revision BIGINT NOT NULL, event_id UUID NOT NULL, deleted BOOLEAN NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (producer, aggregate_key)
);

ALTER TABLE resource_index_lifecycle ADD COLUMN study_server_id UUID;
ALTER TABLE resource_index_lifecycle ADD COLUMN source_event_id UUID;
ALTER TABLE resource_index_lifecycle ADD COLUMN source_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE resource_index_lifecycle ADD COLUMN job_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE resource_index_lifecycle ADD COLUMN job_lease_id UUID;
ALTER TABLE resource_index_lifecycle ADD COLUMN job_lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE resource_index_lifecycle ADD COLUMN job_retry_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_resource_ingestion_jobs ON resource_index_lifecycle (status, job_retry_at, job_lease_until);
