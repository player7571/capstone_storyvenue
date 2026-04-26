CREATE TABLE IF NOT EXISTS service_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    provider TEXT NOT NULL DEFAULT 'kakao',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_service_refresh_tokens_user_id
    ON service_refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_service_refresh_tokens_revoked_at
    ON service_refresh_tokens (revoked_at);
