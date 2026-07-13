CREATE TABLE cohort_roles (
    cohort_id UUID NOT NULL REFERENCES cohorts(id),
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (cohort_id, user_id),
    CONSTRAINT cohort_roles_role_check CHECK (role IN ('TA'))
);

CREATE INDEX idx_cohort_roles_user_id ON cohort_roles(user_id);
