ALTER TABLE office_hours_sessions
    ADD CONSTRAINT office_hours_sessions_status_check
    CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED'));

ALTER TABLE office_hours_waitlist_entries
    ADD CONSTRAINT office_hours_waitlist_entries_status_check
    CHECK (status IN ('WAITING', 'ADMITTED', 'LEFT'));
