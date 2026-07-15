ALTER TABLE study_assistant_audit_records ADD COLUMN llm_provider VARCHAR(64);
ALTER TABLE study_assistant_audit_records ADD COLUMN llm_model VARCHAR(128);
ALTER TABLE study_assistant_audit_records ADD COLUMN llm_used BOOLEAN DEFAULT FALSE NOT NULL;
