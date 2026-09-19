CREATE TABLE durable_consumer_lock (id INT PRIMARY KEY);
INSERT INTO durable_consumer_lock VALUES (1);
CREATE TABLE durable_event_cursor (
    producer VARCHAR(32) NOT NULL, aggregate_key VARCHAR(300) NOT NULL,
    revision BIGINT NOT NULL, event_id UUID NOT NULL, deleted BOOLEAN NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (producer, aggregate_key)
);
