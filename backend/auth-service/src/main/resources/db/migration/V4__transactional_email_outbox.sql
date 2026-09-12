CREATE TABLE auth_email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(320),
    subject VARCHAR(256),
    body_text TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT auth_email_outbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'EXPIRED')),
    CONSTRAINT auth_email_outbox_payload CHECK (
        (status = 'PENDING' AND recipient IS NOT NULL AND subject IS NOT NULL AND body_text IS NOT NULL)
        OR (status <> 'PENDING' AND recipient IS NULL AND subject IS NULL AND body_text IS NULL)
    )
);

CREATE INDEX idx_auth_email_outbox_due ON auth_email_outbox(status, next_attempt_at);
