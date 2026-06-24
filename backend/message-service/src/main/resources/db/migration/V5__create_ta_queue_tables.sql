CREATE TABLE ta_queue_items (
    id UUID PRIMARY KEY,
    cohort_id UUID NOT NULL,
    course_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    support_question_id UUID NOT NULL,
    requester_user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    picked_up_by_user_id UUID,
    resolved_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    picked_up_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_ta_queue_items_cohort_status_created
    ON ta_queue_items (cohort_id, status, created_at);

CREATE UNIQUE INDEX idx_ta_queue_items_active_support_question
    ON ta_queue_items (support_question_id)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
