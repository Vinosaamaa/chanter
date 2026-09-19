ALTER TABLE search_index_entries ALTER COLUMN study_server_id DROP NOT NULL;
ALTER TABLE search_index_entries ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE search_index_entries ADD COLUMN href VARCHAR(1024);
ALTER TABLE search_index_entries ADD COLUMN channel_id UUID;
ALTER TABLE search_index_entries ADD COLUMN channel_scope VARCHAR(32);
CREATE TABLE durable_consumer_lock (id INT PRIMARY KEY);
INSERT INTO durable_consumer_lock VALUES (1);
CREATE TABLE durable_event_cursor (
    producer VARCHAR(32) NOT NULL, aggregate_key VARCHAR(300) NOT NULL,
    revision BIGINT NOT NULL, event_id UUID NOT NULL, deleted BOOLEAN NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (producer, aggregate_key)
);
