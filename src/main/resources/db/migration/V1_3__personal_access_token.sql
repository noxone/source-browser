CREATE TABLE personal_access_token (
    id           BIGSERIAL    PRIMARY KEY,
    owner        VARCHAR(255) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ
);

CREATE INDEX idx_pat_owner ON personal_access_token (owner);
CREATE INDEX idx_pat_token_hash ON personal_access_token (token_hash);
