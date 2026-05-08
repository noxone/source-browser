CREATE TABLE user_account (
    id             BIGSERIAL    PRIMARY KEY,
    principal_name TEXT         NOT NULL UNIQUE,
    is_admin       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_account_principal_name ON user_account (principal_name);
