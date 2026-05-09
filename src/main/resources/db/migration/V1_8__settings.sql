-- Settings table for runtime-configurable application settings.
-- Values are stored as strings and interpreted by the application.
CREATE TABLE setting (
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);
