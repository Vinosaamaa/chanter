CREATE TABLE ai_generation_usage (
    id UUID PRIMARY KEY,
    study_server_id UUID NOT NULL,
    support_question_id UUID NOT NULL,
    learner_user_id UUID NOT NULL,
    selection_id VARCHAR(48) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    requested_model VARCHAR(128) NOT NULL,
    resolved_model VARCHAR(128),
    provider_request_id VARCHAR(128),
    reserved_tokens BIGINT NOT NULL CHECK (reserved_tokens > 0),
    input_tokens INTEGER CHECK (input_tokens >= 0),
    output_tokens INTEGER CHECK (output_tokens >= 0),
    cache_read_tokens INTEGER CHECK (cache_read_tokens >= 0),
    cache_write_tokens INTEGER CHECK (cache_write_tokens >= 0),
    reasoning_tokens INTEGER CHECK (reasoning_tokens >= 0),
    measured BOOLEAN NOT NULL DEFAULT FALSE,
    outcome VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    estimated_cost_usd DECIMAL(20,8),
    price_version VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_ai_generation_daily_usage ON ai_generation_usage (study_server_id, created_at);
CREATE INDEX idx_ai_generation_question ON ai_generation_usage (support_question_id, outcome);
