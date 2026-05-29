-- Per-token LSP hover cache: hover markdown + definition location collected at scan time.
-- Uses the same two-phase (unpublished→published) pattern as token_stream.

CREATE TABLE token_hover (
    id          BIGSERIAL   PRIMARY KEY,
    file_id     BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    line        INT         NOT NULL,
    col         INT         NOT NULL,
    markdown    TEXT,
    def_path    TEXT,
    def_line    INT,
    def_col     INT,
    scan_job_id BIGINT      REFERENCES scan_job(id) ON DELETE SET NULL,
    published   BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_token_hover_scan_job  ON token_hover(scan_job_id);
CREATE INDEX idx_token_hover_published ON token_hover(file_id, line, col)
    WHERE published = TRUE;
