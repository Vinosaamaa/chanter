ALTER TABLE course_channels ADD COLUMN cohort_id UUID;
ALTER TABLE course_channels ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

-- Before Cohort-scoped channels, Course creation always produced exactly one Cohort.
-- Keep the scalar subquery strict so ambiguous or orphaned legacy data fails migration
-- instead of silently assigning channels to an arbitrary Cohort.
UPDATE course_channels cc
SET cohort_id = (
    SELECT c.id
    FROM cohorts c
    WHERE c.course_id = cc.course_id
);

ALTER TABLE course_channels ALTER COLUMN cohort_id SET NOT NULL;
ALTER TABLE course_channels
    ADD CONSTRAINT fk_course_channels_cohort
    FOREIGN KEY (cohort_id) REFERENCES cohorts(id) ON DELETE CASCADE;

CREATE INDEX idx_course_channels_cohort_position
    ON course_channels (cohort_id, position);

CREATE INDEX idx_course_channels_cohort_name
    ON course_channels (cohort_id, name);
