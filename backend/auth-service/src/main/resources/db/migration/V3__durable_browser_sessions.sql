CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    user_agent VARCHAR(255) NOT NULL DEFAULT ''
);
CREATE INDEX auth_sessions_user_id_idx ON auth_sessions (user_id);
ALTER TABLE auth_refresh_tokens ADD COLUMN session_id UUID;
ALTER TABLE auth_refresh_tokens ADD COLUMN consumed_at TIMESTAMP;

-- Old JavaScript-readable credentials have no trustworthy family history. Require a fresh login.
INSERT INTO auth_sessions (id, user_id, created_at, last_used_at, expires_at, revoked_at)
SELECT id, user_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, expires_at, CURRENT_TIMESTAMP
FROM auth_refresh_tokens;
UPDATE auth_refresh_tokens SET session_id = id, revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP);
ALTER TABLE auth_refresh_tokens ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE auth_refresh_tokens ADD CONSTRAINT auth_refresh_token_session_fk
    FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE;
CREATE INDEX auth_refresh_tokens_session_id_idx ON auth_refresh_tokens (session_id);
