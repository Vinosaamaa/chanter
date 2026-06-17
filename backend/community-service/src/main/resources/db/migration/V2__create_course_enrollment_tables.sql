CREATE TABLE courses (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL REFERENCES study_servers(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    instructor_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE course_roles (
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (course_id, user_id, role)
);

CREATE TABLE cohorts (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE course_channels (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE (course_id, name)
);

CREATE TABLE cohort_enrollments (
    cohort_id UUID NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    learner_user_id UUID NOT NULL,
    enrolled_by_user_id UUID NOT NULL,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (cohort_id, learner_user_id)
);
