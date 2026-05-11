-- Supports joining unresolved references to symbols by qualified name
-- (used when symbol_id could not be populated at index time).
CREATE INDEX IF NOT EXISTS idx_reference_unresolved_name
    ON reference (unresolved_name)
    WHERE unresolved_name IS NOT NULL;
