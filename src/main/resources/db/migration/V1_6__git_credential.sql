-- ── Git Credential ────────────────────────────────────────────────────────────
-- Stores AES-256-GCM encrypted secrets scoped to a repository or provider group.
-- scope_type discriminates between 'REPOSITORY' and 'GROUP'.
-- scope_id is the numeric id of the owning repository or git_provider_group row.
-- The unique constraint ensures at most one credential per owning entity.
-- The plaintext secret is never stored; only the base64(iv):base64(ciphertext) value.
CREATE TABLE git_credential (
    id               BIGSERIAL    PRIMARY KEY,
    scope_type       TEXT         NOT NULL,
    scope_id         BIGINT       NOT NULL,
    description      TEXT,
    encrypted_secret TEXT         NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uidx_git_credential_scope ON git_credential (scope_type, scope_id);
CREATE INDEX idx_git_credential_scope_type ON git_credential (scope_type);
