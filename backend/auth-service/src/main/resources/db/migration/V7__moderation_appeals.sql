CREATE TABLE moderation_appeal_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id),
    restriction_id UUID NOT NULL REFERENCES moderation_restrictions(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX moderation_appeal_tokens_expiry_idx ON moderation_appeal_tokens(expires_at);

CREATE TABLE moderation_appeals (
    id UUID PRIMARY KEY,
    restriction_id UUID NOT NULL REFERENCES moderation_restrictions(id),
    user_id UUID NOT NULL REFERENCES auth_users(id),
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','UPHELD','REVERSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolution VARCHAR(2000),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID REFERENCES platform_operators(user_id)
);
CREATE INDEX moderation_appeals_restriction_idx ON moderation_appeals(restriction_id,created_at,id);
CREATE INDEX moderation_appeals_pending_idx ON moderation_appeals(status,created_at,id);
