ALTER TABLE user_account
    ADD COLUMN is_service_account BOOLEAN NOT NULL DEFAULT FALSE;
