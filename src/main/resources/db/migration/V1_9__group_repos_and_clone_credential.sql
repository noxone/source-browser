-- ── Group-discovered repositories ────────────────────────────────────────────
-- Repositories may be added manually (source_group_id IS NULL) or auto-discovered
-- from a git_provider_group (source_group_id IS NOT NULL).
-- When a group is deleted, its discovered repositories are cascade-deleted.

ALTER TABLE repository ADD COLUMN source_group_id BIGINT REFERENCES git_provider_group(id) ON DELETE CASCADE;

-- Drop the global unique constraint on name; manual repos keep it via a partial index.
ALTER TABLE repository DROP CONSTRAINT repository_name_key;
CREATE UNIQUE INDEX idx_repository_name_manual ON repository(name) WHERE source_group_id IS NULL;

-- Group repos are unique by (group, remote_url); duplicate names within a group are fine.
CREATE UNIQUE INDEX idx_repository_group_url ON repository(source_group_id, remote_url) WHERE source_group_id IS NOT NULL;
CREATE INDEX idx_repository_source_group ON repository(source_group_id);
