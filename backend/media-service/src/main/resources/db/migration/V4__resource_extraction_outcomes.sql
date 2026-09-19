-- Durable source events use the accepted V3 outbox; scope is enriched from the owning Course.
ALTER TABLE course_resources ADD COLUMN ingestion_signals VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE course_resources ADD COLUMN study_server_id UUID;
ALTER TABLE course_resources ADD COLUMN ingestion_event_id UUID;
-- COMPLETE used to include formats skipped without parsing. Recheck rather than claiming readiness.
UPDATE course_resources SET ingestion_status='PENDING', retry_at=NULL
WHERE state='AVAILABLE' AND ai_approved=TRUE AND ingestion_status='COMPLETE';
