-- ── Additional filter options for Git provider groups ─────────────────────────
-- is_shared_omitted: exclude repositories that are shared into the group
--   (GitLab: projects shared via group access; not applicable to GitHub)
-- is_imported_omitted: exclude repositories that were imported from another source
--   (GitLab: projects with imported=true; not applicable to GitHub)
ALTER TABLE git_provider_group
    ADD COLUMN is_shared_omitted   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN is_imported_omitted BOOLEAN NOT NULL DEFAULT false;
