-- Fix CHECK constraints to use uppercase enum values
-- to match Java @Enumerated(EnumType.STRING) storage format

ALTER TABLE scan_job DROP CONSTRAINT chk_scan_job_status;
ALTER TABLE scan_job ADD CONSTRAINT chk_scan_job_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED'));

ALTER TABLE scan_job DROP CONSTRAINT chk_scan_job_trigger;
ALTER TABLE scan_job ADD CONSTRAINT chk_scan_job_trigger
    CHECK (trigger_type IN ('WEBHOOK', 'CRON', 'MANUAL'));
