-- Add qualified_name to reference so the FQN of the referenced symbol is always
-- stored, even when symbol_id (the FK) is also present. This enables clients to
-- display / navigate to the target regardless of whether it is indexed locally.
ALTER TABLE reference
    ADD COLUMN IF NOT EXISTS qualified_name VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_reference_qualified_name
    ON reference (qualified_name)
    WHERE published = true;
