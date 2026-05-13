-- Phase-3 token stream indexing: each indexed source file gets a gzip-compressed
-- JSON token array stored as BYTEA. Uses the same two-phase (unpublished→published)
-- pattern as document and symbol indexing.

CREATE TABLE token_stream (
    id          BIGSERIAL   PRIMARY KEY,
    file_id     BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    data        BYTEA       NOT NULL,  -- gzip-compressed JSON token array
    scan_job_id BIGINT      REFERENCES scan_job(id),
    published   BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_token_stream_file        ON token_stream(file_id);
CREATE INDEX idx_token_stream_scan_job    ON token_stream(scan_job_id);
CREATE INDEX idx_token_stream_unpublished ON token_stream(scan_job_id) WHERE NOT published;
