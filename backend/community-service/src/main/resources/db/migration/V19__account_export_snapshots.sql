CREATE TABLE data_export_lock (id INT PRIMARY KEY);
INSERT INTO data_export_lock VALUES (1);
CREATE TABLE data_export_account_tombstones (account_id UUID PRIMARY KEY, deleted_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE data_export_snapshots (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL, byte_size BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX data_export_expiry ON data_export_snapshots(expires_at);
CREATE TABLE data_export_entries (
    snapshot_id UUID NOT NULL REFERENCES data_export_snapshots(id) ON DELETE CASCADE,
    ordinal INT NOT NULL, entry_path VARCHAR(120) NOT NULL, media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL DEFAULT 0, page_count INT NOT NULL DEFAULT 0, sha256 VARCHAR(64),
    PRIMARY KEY(snapshot_id, ordinal), UNIQUE(snapshot_id, entry_path)
);
CREATE TABLE data_export_entry_scopes (
    snapshot_id UUID NOT NULL, entry_ordinal INT NOT NULL, access_kind VARCHAR(24) NOT NULL, access_id UUID NOT NULL,
    expected_digest VARCHAR(64),
    PRIMARY KEY(snapshot_id,entry_ordinal,access_kind,access_id),
    FOREIGN KEY(snapshot_id,entry_ordinal) REFERENCES data_export_entries(snapshot_id,ordinal) ON DELETE CASCADE
);
CREATE TABLE data_export_pages (
    snapshot_id UUID NOT NULL, entry_ordinal INT NOT NULL, ordinal INT NOT NULL, payload BYTEA NOT NULL,
    PRIMARY KEY(snapshot_id, entry_ordinal, ordinal),
    FOREIGN KEY(snapshot_id, entry_ordinal) REFERENCES data_export_entries(snapshot_id, ordinal) ON DELETE CASCADE
);
CREATE TABLE durable_consumer_lock (id INT PRIMARY KEY);
INSERT INTO durable_consumer_lock VALUES (1);
CREATE TABLE durable_event_cursor (
    producer VARCHAR(32) NOT NULL, aggregate_key VARCHAR(300) NOT NULL,
    revision BIGINT NOT NULL, event_id UUID NOT NULL, deleted BOOLEAN NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (producer, aggregate_key)
);


CREATE TABLE lifecycle_account_ownership (
    account_id UUID PRIMARY KEY,
    preparation_job UUID,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (terminal=FALSE OR preparation_job IS NULL)
);

CREATE TABLE lifecycle_reapply_head (id INT PRIMARY KEY, revision BIGINT NOT NULL, digest VARCHAR(64) NOT NULL);
INSERT INTO lifecycle_reapply_head VALUES (1,0,'0000000000000000000000000000000000000000000000000000000000000000');
CREATE TABLE lifecycle_reapply_entries (revision BIGINT PRIMARY KEY, digest VARCHAR(64) NOT NULL);
CREATE TABLE lifecycle_terminal_targets (
    target_kind VARCHAR(16) NOT NULL CHECK(target_kind IN ('ACCOUNT','STUDY_SERVER','RESOURCE')), target_id UUID NOT NULL,
    revision BIGINT NOT NULL UNIQUE CHECK(revision>0), event_id UUID NOT NULL UNIQUE, digest VARCHAR(64) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL, cleanup_state VARCHAR(16) NOT NULL CHECK(cleanup_state IN ('PENDING','PRESERVED','COMPLETE')),
    PRIMARY KEY(target_kind,target_id)
);

CREATE TABLE lifecycle_deleted_server_scopes (
    study_server_id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, revision BIGINT NOT NULL UNIQUE,
    digest VARCHAR(64) NOT NULL, available BOOLEAN NOT NULL
);
CREATE TABLE lifecycle_deleted_server_scope_ids (
    study_server_id UUID NOT NULL REFERENCES lifecycle_deleted_server_scopes(study_server_id),
    scope_kind VARCHAR(16) NOT NULL CHECK(scope_kind IN ('COURSE','CHANNEL')), scope_id UUID NOT NULL,
    PRIMARY KEY(study_server_id,scope_kind,scope_id)
);
