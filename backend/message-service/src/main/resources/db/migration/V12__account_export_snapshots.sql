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
CREATE TABLE lifecycle_reapply_head (id INT PRIMARY KEY, revision BIGINT NOT NULL, digest VARCHAR(64) NOT NULL);
INSERT INTO lifecycle_reapply_head VALUES (1,0,'0000000000000000000000000000000000000000000000000000000000000000');
CREATE TABLE lifecycle_reapply_entries (revision BIGINT PRIMARY KEY, digest VARCHAR(64) NOT NULL);
CREATE TABLE lifecycle_terminal_targets (
    target_kind VARCHAR(16) NOT NULL CHECK(target_kind IN ('ACCOUNT','STUDY_SERVER','RESOURCE')), target_id UUID NOT NULL,
    revision BIGINT NOT NULL UNIQUE CHECK(revision>0), event_id UUID NOT NULL UNIQUE, digest VARCHAR(64) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL, cleanup_state VARCHAR(16) NOT NULL CHECK(cleanup_state IN ('PENDING','PRESERVED','COMPLETE')),
    PRIMARY KEY(target_kind,target_id)
);

CREATE TABLE lifecycle_scope_imports (
            study_server_id UUID NOT NULL, scope_kind VARCHAR(8) NOT NULL CHECK(scope_kind IN ('COURSE','CHANNEL')),
            revision BIGINT NOT NULL, event_id UUID NOT NULL, terminal_digest VARCHAR(64) NOT NULL,
            total_count BIGINT NOT NULL CHECK(total_count>=0), scope_digest VARCHAR(64) NOT NULL,
            received_count BIGINT NOT NULL, after_id UUID NOT NULL, rolling_digest VARCHAR(64) NOT NULL, ready BOOLEAN NOT NULL, basis_digest VARCHAR(64) NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_scope_import_ids (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,scope_id UUID NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,scope_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_scope_imports(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_scope_import_pages (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,after_id UUID NOT NULL,page_digest VARCHAR(64) NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,after_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_scope_imports(study_server_id,scope_kind)
        );

CREATE TABLE lifecycle_recovery_scopes (
            study_server_id UUID NOT NULL, scope_kind VARCHAR(8) NOT NULL CHECK(scope_kind IN ('COURSE','CHANNEL')),
            revision BIGINT NOT NULL, event_id UUID NOT NULL, terminal_digest VARCHAR(64) NOT NULL,
            total_count BIGINT NOT NULL CHECK(total_count>=0), scope_digest VARCHAR(64) NOT NULL,
            received_count BIGINT NOT NULL, after_id UUID NOT NULL, rolling_digest VARCHAR(64) NOT NULL, ready BOOLEAN NOT NULL,
            basis_digest VARCHAR(64) NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_recovery_scope_ids (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,scope_id UUID NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,scope_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_recovery_scopes(study_server_id,scope_kind)
        );
        CREATE TABLE lifecycle_recovery_scope_pages (
            study_server_id UUID NOT NULL,scope_kind VARCHAR(8) NOT NULL,after_id UUID NOT NULL,page_digest VARCHAR(64) NOT NULL,
            PRIMARY KEY(study_server_id,scope_kind,after_id),
            FOREIGN KEY(study_server_id,scope_kind) REFERENCES lifecycle_recovery_scopes(study_server_id,scope_kind)
        );

CREATE INDEX lifecycle_scope_import_reverse ON lifecycle_scope_import_ids(scope_kind,scope_id,study_server_id);
CREATE INDEX lifecycle_recovery_scope_reverse ON lifecycle_recovery_scope_ids(scope_kind,scope_id,study_server_id);

CREATE TABLE lifecycle_erased_content (
    target_kind VARCHAR(16) NOT NULL, target_id UUID NOT NULL, revision BIGINT NOT NULL,
    event_id UUID NOT NULL, terminal_digest VARCHAR(64) NOT NULL,
    source_kind VARCHAR(24) NOT NULL CHECK(source_kind IN ('MESSAGE','QUESTION','QUESTION_PREVIEW','FAQ')),
    source_id UUID NOT NULL,
    PRIMARY KEY(target_kind,target_id,source_kind,source_id)
);
CREATE INDEX lifecycle_erased_content_reverse ON lifecycle_erased_content(source_kind,source_id);

CREATE TABLE lifecycle_answer_outcomes (
    question_id UUID PRIMARY KEY, answer_id UUID NOT NULL UNIQUE, channel_id UUID NOT NULL,
    author_id UUID NOT NULL, status VARCHAR(24) NOT NULL CHECK(status IN ('AI_ANSWERED','AI_LOW_CONFIDENCE'))
);
CREATE INDEX lifecycle_answer_outcomes_author_idx ON lifecycle_answer_outcomes(author_id,question_id);
CREATE INDEX lifecycle_answer_outcomes_channel_idx ON lifecycle_answer_outcomes(channel_id,question_id);

ALTER TABLE lifecycle_terminal_targets ADD COLUMN previous_digest VARCHAR(64) NOT NULL;
CREATE TABLE lifecycle_terminal_delivery (
    target_kind VARCHAR(16) NOT NULL,target_id UUID NOT NULL,job_id UUID NOT NULL,reported_state VARCHAR(16) NOT NULL,
    PRIMARY KEY(target_kind,target_id)
);
