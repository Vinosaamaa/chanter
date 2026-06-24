ALTER TABLE study_servers
    ADD COLUMN plan_tier VARCHAR(32) NOT NULL DEFAULT 'STARTER';

ALTER TABLE study_servers
    ADD CONSTRAINT study_servers_plan_tier_check
        CHECK (plan_tier IN ('STARTER', 'PRO', 'ORGANIZATION'));
