CREATE TABLE approved_faqs (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    approved_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE approved_faq_source_questions (
    approved_faq_id UUID NOT NULL REFERENCES approved_faqs(id) ON DELETE CASCADE,
    support_question_id UUID NOT NULL REFERENCES support_questions(id),
    PRIMARY KEY (approved_faq_id, support_question_id)
);

CREATE INDEX idx_approved_faqs_course_id ON approved_faqs (course_id, updated_at DESC);
