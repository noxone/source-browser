-- Drop the local_path column; the filesystem location is now derived from
-- sourceviewer.repos.base-path configuration + repository name.
ALTER TABLE repository DROP COLUMN local_path;
