ALTER TABLE cohort_enrollments
    ADD COLUMN assigned_ta_user_id UUID;

CREATE INDEX idx_cohort_enrollments_assigned_ta
    ON cohort_enrollments (cohort_id, assigned_ta_user_id);
