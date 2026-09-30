CREATE TABLE sea_channel_link_challenge (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES sea_account(id),
    channel_id VARCHAR(64) NOT NULL,
    operations TEXT NOT NULL,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX sea_channel_link_challenge_active_idx
    ON sea_channel_link_challenge(code_hash, expires_at);
