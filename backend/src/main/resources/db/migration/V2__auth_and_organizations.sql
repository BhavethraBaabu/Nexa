-- Phase 1: organizations, users, authentication tokens, invitations and audit logs.
-- See PRD sections 7, 28 and 38.

CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    name             VARCHAR(120) NOT NULL,
    -- Always stored lower-cased by the application.
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_organization ON users (organization_id);

-- Refresh tokens are opaque random values; only their SHA-256 hash is stored.
-- Tokens issued from the same login share a family_id so reuse of a rotated
-- token can revoke the whole family.
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id    UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE invitations (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    email            VARCHAR(254) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    token_hash       VARCHAR(64)  NOT NULL,
    invited_by       UUID         NOT NULL REFERENCES users (id),
    expires_at       TIMESTAMPTZ  NOT NULL,
    accepted_at      TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_invitations_hash UNIQUE (token_hash)
);

CREATE INDEX idx_invitations_organization ON invitations (organization_id);

CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    user_id          UUID REFERENCES users (id),
    action           VARCHAR(64)  NOT NULL,
    resource_type    VARCHAR(64)  NOT NULL,
    resource_id      UUID,
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_org_created ON audit_logs (organization_id, created_at DESC);
