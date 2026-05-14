-- Switch full-text search to 'simple' dictionary (no stemming / stop-words).
-- The GENERATED column must be dropped and re-added to change its expression;
-- the dependent GIN index is dropped automatically with the column.
ALTER TABLE document
    DROP COLUMN search_vector,
    ADD  COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

-- Recreate GIN index as a partial index covering only published rows.
-- All search queries filter WHERE published = true, so the planner can use
-- this smaller index without a residual published-filter step.
CREATE INDEX idx_document_search_vector
    ON document USING GIN(search_vector)
    WHERE published = true;

-- Partial indexes for published symbol reads
CREATE INDEX idx_symbol_name_published
    ON symbol(name) WHERE published = true;
CREATE INDEX idx_symbol_qualified_name_published
    ON symbol(qualified_name) WHERE published = true;
CREATE INDEX idx_symbol_file_published
    ON symbol(file_id) WHERE published = true;

-- Partial indexes for published reference reads
CREATE INDEX idx_reference_symbol_published
    ON reference(symbol_id) WHERE published = true;
CREATE INDEX idx_reference_file_published
    ON reference(file_id) WHERE published = true;

-- Partial index for published token_stream reads
CREATE INDEX idx_token_stream_file_published
    ON token_stream(file_id) WHERE published = true;
