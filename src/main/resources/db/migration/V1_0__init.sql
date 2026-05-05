-- ============================================================
-- V1_0__init.sql
-- Initial database schema for the Java Source Viewer.
-- ============================================================

-- ── Repository ────────────────────────────────────────────────────────────────
-- Represents a single Git repository that is indexed.
CREATE TABLE repository (
    id              BIGSERIAL       PRIMARY KEY,
    name            TEXT            NOT NULL UNIQUE,
    remote_url      TEXT,
    local_path      TEXT            NOT NULL,
    default_branch  TEXT            NOT NULL DEFAULT 'main',
    last_scanned_at TIMESTAMPTZ,
    last_commit_sha TEXT
);

-- ── Source File ───────────────────────────────────────────────────────────────
-- A single file within a repository branch.
CREATE TABLE source_file (
    id          BIGSERIAL   PRIMARY KEY,
    repository_id BIGINT    NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    branch      TEXT        NOT NULL DEFAULT 'main',
    path        TEXT        NOT NULL,
    content_sha TEXT        NOT NULL,
    language    TEXT        NOT NULL DEFAULT 'java',
    indexed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, branch, path)
);

CREATE INDEX idx_source_file_repository ON source_file(repository_id);
CREATE INDEX idx_source_file_path       ON source_file(path);

-- ── Symbol ────────────────────────────────────────────────────────────────────
-- A declaration in the source code (class, method, field, ...).
CREATE TABLE symbol (
    id              BIGSERIAL   PRIMARY KEY,
    file_id         BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    qualified_name  TEXT        NOT NULL,
    signature       TEXT,
    line_start      INTEGER,
    line_end        INTEGER,
    column_start    INTEGER,
    modifiers       TEXT[]      NOT NULL DEFAULT '{}',
    extras          JSONB
);

CREATE INDEX idx_symbol_name           ON symbol(name);
CREATE INDEX idx_symbol_qualified_name ON symbol(qualified_name);
CREATE INDEX idx_symbol_file           ON symbol(file_id);
CREATE INDEX idx_symbol_kind           ON symbol(kind);

-- ── Reference ─────────────────────────────────────────────────────────────────
-- A usage of a symbol at a location in the source code.
CREATE TABLE reference (
    id              BIGSERIAL   PRIMARY KEY,
    file_id         BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    symbol_id       BIGINT      REFERENCES symbol(id) ON DELETE SET NULL,
    unresolved_name TEXT,
    kind            TEXT        NOT NULL,
    line            INTEGER,
    column_start    INTEGER
);

CREATE INDEX idx_reference_symbol ON reference(symbol_id);
CREATE INDEX idx_reference_file   ON reference(file_id);

-- ── Full-text Document ────────────────────────────────────────────────────────
-- Full-text index for free search over source code, configuration, documentation.
CREATE TABLE document (
    id          BIGSERIAL   PRIMARY KEY,
    file_id     BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    document_type TEXT      NOT NULL,
    content     TEXT        NOT NULL,
    search_vector TSVECTOR  GENERATED ALWAYS AS (
                    to_tsvector('english', content)
                  ) STORED
);

CREATE INDEX idx_document_search_vector ON document USING GIN(search_vector);
CREATE INDEX idx_document_file          ON document(file_id);

-- ── Scan Job ──────────────────────────────────────────────────────────────────
-- Logs each scan operation (triggered by webhook or cron).
CREATE TABLE scan_job (
    id              BIGSERIAL   PRIMARY KEY,
    repository_id   BIGINT      NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
    trigger_type    TEXT        NOT NULL,
    commit_sha      TEXT,
    status          TEXT        NOT NULL DEFAULT 'queued',
    queued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    files_scanned   INTEGER     NOT NULL DEFAULT 0,
    error_message   TEXT,

    CONSTRAINT chk_scan_job_status
        CHECK (status IN ('queued', 'running', 'done', 'failed')),
    CONSTRAINT chk_scan_job_trigger
        CHECK (trigger_type IN ('webhook', 'cron', 'manual'))
);

CREATE INDEX idx_scan_job_repository ON scan_job(repository_id);
CREATE INDEX idx_scan_job_status     ON scan_job(status);
CREATE INDEX idx_scan_job_queued_at  ON scan_job(queued_at);
