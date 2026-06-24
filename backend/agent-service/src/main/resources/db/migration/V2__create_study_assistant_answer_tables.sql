CREATE TABLE study_assistant_answers (
    id UUID PRIMARY KEY,
    support_question_id UUID NOT NULL UNIQUE,
    channel_id UUID NOT NULL,
    study_server_id UUID NOT NULL,
    learner_user_id UUID NOT NULL,
    question_body TEXT NOT NULL,
    answer_body TEXT NOT NULL,
    confidence VARCHAR(16) NOT NULL CHECK (confidence IN ('HIGH', 'LOW')),
    handoff_recommended BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE study_assistant_answer_sources (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL REFERENCES study_assistant_answers(id) ON DELETE CASCADE,
    resource_id UUID NOT NULL,
    resource_title TEXT NOT NULL,
    excerpt TEXT NOT NULL
);

CREATE INDEX idx_study_assistant_answer_sources_answer_id
    ON study_assistant_answer_sources (answer_id);

CREATE TABLE study_assistant_audit_records (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL REFERENCES study_assistant_answers(id) ON DELETE CASCADE,
    study_server_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    learner_user_id UUID NOT NULL,
    invocation_type VARCHAR(32) NOT NULL CHECK (invocation_type IN ('GROUNDED_ANSWER', 'LOW_CONFIDENCE_HANDOFF')),
    confidence VARCHAR(16) NOT NULL CHECK (confidence IN ('HIGH', 'LOW')),
    source_count INTEGER NOT NULL CHECK (source_count >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_study_assistant_audit_records_answer_id
    ON study_assistant_audit_records (answer_id);
