ALTER TABLE courses ADD COLUMN published BOOLEAN DEFAULT TRUE;
UPDATE courses SET published = TRUE WHERE published IS NULL;
ALTER TABLE courses ALTER COLUMN published SET NOT NULL;

ALTER TABLE cohorts ADD COLUMN enrollment_policy VARCHAR(32) DEFAULT 'INVITE_ONLY';
UPDATE cohorts SET enrollment_policy = 'INVITE_ONLY' WHERE enrollment_policy IS NULL;
ALTER TABLE cohorts ALTER COLUMN enrollment_policy SET NOT NULL;
ALTER TABLE cohorts ALTER COLUMN enrollment_policy SET DEFAULT 'OPEN';
ALTER TABLE cohorts
    ADD CONSTRAINT chk_cohorts_enrollment_policy
    CHECK (enrollment_policy IN ('OPEN', 'INVITE_ONLY', 'OPENING_SOON', 'CLOSED'));

CREATE INDEX idx_courses_discovery
    ON courses (study_server_id, published, title);
