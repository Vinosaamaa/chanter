CREATE UNIQUE INDEX uq_ta_queue_open_support_question
    ON ta_queue_items (support_question_id)
    WHERE status IN ('OPEN', 'PICKED_UP');
