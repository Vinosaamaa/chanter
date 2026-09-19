CREATE TABLE native_companion_requests (
    id UUID PRIMARY KEY REFERENCES ai_generation_usage(id),
    channel_id UUID NOT NULL,
    question_id UUID NOT NULL,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    model VARCHAR(128) NOT NULL,
    evidence_json TEXT,
    prompt_hash VARCHAR(64) NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('ISSUED','ACCEPTING','ACCEPTED','REJECTED','EXPIRED')),
    accept_until TIMESTAMP WITH TIME ZONE NOT NULL,
    client_input_tokens INTEGER CHECK (client_input_tokens >= 0),
    client_output_tokens INTEGER CHECK (client_output_tokens >= 0),
    provenance VARCHAR(32) NOT NULL DEFAULT 'native-client-report'
);
CREATE INDEX idx_native_companion_expiry ON native_companion_requests(outcome, accept_until);
