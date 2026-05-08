-- ── Git Provider Group ────────────────────────────────────────────────────────
-- Stores configuration for a group or organization on a Git hosting provider
-- (e.g. a GitLab group or a GitHub organization) whose repositories shall be
-- discovered and indexed by the source viewer.
CREATE TABLE git_provider_group (
    id                  BIGSERIAL   PRIMARY KEY,
    name                TEXT        NOT NULL UNIQUE,
    provider_type       TEXT        NOT NULL,
    group_path          TEXT        NOT NULL,
    base_url            TEXT,
    is_archived_omitted BOOLEAN     NOT NULL DEFAULT false,
    is_forked_omitted   BOOLEAN     NOT NULL DEFAULT false
);

CREATE INDEX idx_git_provider_group_provider_type ON git_provider_group(provider_type);
