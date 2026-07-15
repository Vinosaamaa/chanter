CREATE TABLE study_assistant_answer_helpful (
    answer_id UUID NOT NULL REFERENCES study_assistant_answers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (answer_id, user_id)
);

CREATE INDEX idx_study_assistant_answer_helpful_answer_id
    ON study_assistant_answer_helpful (answer_id);
