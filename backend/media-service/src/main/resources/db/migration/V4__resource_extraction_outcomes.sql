-- V3 is reserved for issue #245's durable outbox before this migration is deployed.
ALTER TABLE course_resources ADD COLUMN ingestion_signals VARCHAR(512) NOT NULL DEFAULT '';
-- COMPLETE used to include formats skipped without parsing. Recheck rather than claiming readiness.
UPDATE course_resources SET ingestion_status='PENDING', retry_at=NULL
WHERE state='AVAILABLE' AND ai_approved=TRUE AND ingestion_status='COMPLETE';
