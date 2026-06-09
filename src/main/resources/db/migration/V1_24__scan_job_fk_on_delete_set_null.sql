-- Allow finished scan jobs to be deleted without violating FK constraints.
--
-- The scan_job_id column on document, symbol, reference, token_stream,
-- token_detail, and type_hierarchy is only meaningful during the two-phase
-- indexing window (unpublished → published). Once a job is DONE or FAILED,
-- the column is historical-only on published rows and safe to null out.
--
-- All six FK constraints were created inline (no explicit name), so PostgreSQL
-- auto-generated names following the {table}_{column}_fkey convention.
-- We drop and re-create each one with ON DELETE SET NULL.

ALTER TABLE document
    DROP CONSTRAINT document_scan_job_id_fkey,
    ADD  CONSTRAINT document_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;

ALTER TABLE symbol
    DROP CONSTRAINT symbol_scan_job_id_fkey,
    ADD  CONSTRAINT symbol_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;

ALTER TABLE reference
    DROP CONSTRAINT reference_scan_job_id_fkey,
    ADD  CONSTRAINT reference_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;

ALTER TABLE token_stream
    DROP CONSTRAINT token_stream_scan_job_id_fkey,
    ADD  CONSTRAINT token_stream_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;

ALTER TABLE token_detail
    DROP CONSTRAINT token_detail_scan_job_id_fkey,
    ADD  CONSTRAINT token_detail_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;

ALTER TABLE type_hierarchy
    DROP CONSTRAINT type_hierarchy_scan_job_id_fkey,
    ADD  CONSTRAINT type_hierarchy_scan_job_id_fkey
         FOREIGN KEY (scan_job_id) REFERENCES scan_job(id) ON DELETE SET NULL;
