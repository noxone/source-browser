-- Two-phase symbol indexing: symbols and references are written as unpublished
-- during a scan and atomically activated (published=true) only when the full
-- scan succeeds. Readers always filter published=true so they see a consistent
-- state — either all-old or all-new, never a mix of partially-updated files.

ALTER TABLE symbol
    ADD COLUMN scan_job_id BIGINT REFERENCES scan_job(id),
    ADD COLUMN published   BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE reference
    ADD COLUMN scan_job_id BIGINT REFERENCES scan_job(id),
    ADD COLUMN published   BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_symbol_scan_job       ON symbol(scan_job_id);
CREATE INDEX idx_symbol_unpublished    ON symbol(scan_job_id) WHERE NOT published;
CREATE INDEX idx_reference_scan_job    ON reference(scan_job_id);
CREATE INDEX idx_reference_unpublished ON reference(scan_job_id) WHERE NOT published;
