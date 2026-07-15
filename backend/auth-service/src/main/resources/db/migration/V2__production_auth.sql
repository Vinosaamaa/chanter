ALTER TABLE auth_users ADD COLUMN email_verified BOOLEAN DEFAULT TRUE NOT NULL;

CREATE TABLE auth_email_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX auth_email_tokens_user_id_idx ON auth_email_tokens (user_id);

CREATE TABLE auth_oauth_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_subject)
);

CREATE INDEX auth_oauth_accounts_user_id_idx ON auth_oauth_accounts (user_id);
