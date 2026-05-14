-- Partial index on document.file_id to support repository-filtered full-text search.
-- When a search is scoped to specific repositories, the query joins document → source_file.
-- This index lets the planner drive from source_file (filtered by repository_id)
-- into document via file_id, touching only published=true rows.
CREATE INDEX idx_document_file_published
    ON document(file_id)
    WHERE published = true;
