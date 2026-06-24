CREATE TABLE ta_queue_items (
    id UUID PRIMARY KEY,
    cohort_id UUID NOT NULL,
    support_question_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    learner_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_ta_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ta_queue_items_cohort_status_created
    ON ta_queue_items (cohort_id, status, created_at ASC);

CREATE INDEX idx_ta_queue_items_support_question_id
    ON ta_queue_items (support_question_id);
