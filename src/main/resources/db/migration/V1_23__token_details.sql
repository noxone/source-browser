-- Token-level detail records: rich semantic info extracted per token position during indexing.
-- Queried on-demand when the user clicks a token in the UI.
CREATE TABLE token_detail (
    id           BIGSERIAL    PRIMARY KEY,
    file_id      BIGINT       NOT NULL REFERENCES source_file(id),
    line         INTEGER      NOT NULL,
    column_start INTEGER      NOT NULL,
    detail_type  VARCHAR(50)  NOT NULL,
    detail       JSONB        NOT NULL DEFAULT '{}',
    scan_job_id  BIGINT       REFERENCES scan_job(id),
    published    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX token_detail_file_pos_idx
    ON token_detail(file_id, line, column_start)
    WHERE published = TRUE;

CREATE INDEX token_detail_scan_job_idx
    ON token_detail(scan_job_id)
    WHERE published = FALSE;

-- Type hierarchy: extends/implements relationships extracted during indexing.
-- Used at query time to find implementations of interface methods across all indexed projects.
CREATE TABLE type_hierarchy (
    id                BIGSERIAL    PRIMARY KEY,
    subtype_fqn       VARCHAR(500) NOT NULL,
    supertype_fqn     VARCHAR(500) NOT NULL,
    relationship_kind VARCHAR(20)  NOT NULL, -- EXTENDS | IMPLEMENTS
    scan_job_id       BIGINT       REFERENCES scan_job(id),
    published         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX type_hierarchy_super_idx
    ON type_hierarchy(supertype_fqn)
    WHERE published = TRUE;

CREATE INDEX type_hierarchy_sub_idx
    ON type_hierarchy(subtype_fqn)
    WHERE published = TRUE;

CREATE INDEX type_hierarchy_scan_job_idx
    ON type_hierarchy(scan_job_id)
    WHERE published = FALSE;
