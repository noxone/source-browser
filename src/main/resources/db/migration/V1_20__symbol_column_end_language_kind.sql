-- Add column_end (end column of the declaration name token) and
-- language_kind (raw language-specific keyword, e.g. "protocol", "trait")
-- to the symbol table.
ALTER TABLE symbol
    ADD COLUMN IF NOT EXISTS column_end   INT,
    ADD COLUMN IF NOT EXISTS language_kind VARCHAR(50);
