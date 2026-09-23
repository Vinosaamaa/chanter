ALTER TABLE media_storage_budget ADD COLUMN maintenance_inventory_id UUID;
ALTER TABLE media_storage_budget ADD COLUMN maintenance_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE media_storage_budget ADD CONSTRAINT maintenance_identity_pair CHECK (
    (maintenance_inventory_id IS NULL AND maintenance_started_at IS NULL)
    OR (maintenance_inventory_id IS NOT NULL AND maintenance_started_at IS NOT NULL)
);

CREATE TABLE media_storage_mutations (
    id UUID PRIMARY KEY,
    object_key VARCHAR(150) NOT NULL UNIQUE,
    operation VARCHAR(8) NOT NULL CHECK (operation IN ('PUT','DELETE')),
    outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('ACTIVE','UNKNOWN')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One source-owned, temporary catalogue snapshot per active maintenance window.
-- Encrypted archive manifests retain historical copies; this is not a second ownership catalogue.
CREATE TABLE media_recovery_inventory (
    id INT PRIMARY KEY CHECK(id=1), inventory_id UUID NOT NULL UNIQUE, database_backup_id UUID NOT NULL,
    authority_revision BIGINT NOT NULL, authority_digest VARCHAR(64) NOT NULL,
    namespace_sha256 VARCHAR(64) NOT NULL, captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reference_count INT NOT NULL, reference_digest VARCHAR(64) NOT NULL
);
CREATE TABLE media_recovery_inventory_references (
    inventory_id UUID NOT NULL REFERENCES media_recovery_inventory(inventory_id), ordinal INT NOT NULL,
    resource_id UUID NOT NULL, course_id UUID NOT NULL, reference_kind VARCHAR(16) NOT NULL,
    storage_backend VARCHAR(16) NOT NULL CHECK(storage_backend IN ('local','s3')),
    object_key VARCHAR(150) NOT NULL, byte_size BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL,
    resource_state VARCHAR(32) NOT NULL, source_retained BOOLEAN NOT NULL, terminal BOOLEAN NOT NULL,
    closure_mutation_id UUID, physical_closed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(inventory_id,ordinal), UNIQUE(inventory_id,object_key)
);
