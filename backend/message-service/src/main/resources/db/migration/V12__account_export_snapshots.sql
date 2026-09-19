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
