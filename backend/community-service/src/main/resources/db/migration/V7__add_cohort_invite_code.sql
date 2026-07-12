ALTER TABLE cohorts ADD COLUMN invite_code UUID;

UPDATE cohorts
SET invite_code = gen_random_uuid()
WHERE invite_code IS NULL;

ALTER TABLE cohorts ALTER COLUMN invite_code SET NOT NULL;
