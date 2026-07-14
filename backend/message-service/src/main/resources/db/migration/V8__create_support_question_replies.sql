CREATE TABLE support_question_replies (
    id UUID PRIMARY KEY,
    support_question_id UUID NOT NULL REFERENCES support_questions(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_support_question_replies_question_created
    ON support_question_replies (support_question_id, created_at ASC);
