-- Two-phase document indexing: documents are written as unpublished during a scan
-- and atomically activated (published=true) only when the full scan succeeds.
-- This allows batched inserts per transaction without holding one giant transaction
-- while still guaranteeing that readers never see partially-indexed state.

ALTER TABLE document
    ADD COLUMN scan_job_id BIGINT REFERENCES scan_job(id),
    ADD COLUMN published   BOOLEAN NOT NULL DEFAULT TRUE;

-- Existing rows are already live; new rows during a scan start as published=false.

CREATE INDEX idx_document_scan_job    ON document(scan_job_id);
CREATE INDEX idx_document_unpublished ON document(scan_job_id) WHERE NOT published;
