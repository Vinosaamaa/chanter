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
