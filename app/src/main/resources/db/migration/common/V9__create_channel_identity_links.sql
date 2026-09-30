CREATE TABLE sea_channel_identity_link (
    id UUID PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    external_subject VARCHAR(200) NOT NULL,
    active_identity_key VARCHAR(300) UNIQUE,
    account_id UUID NOT NULL REFERENCES sea_account(id),
    operations TEXT NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX sea_channel_identity_link_account_idx
    ON sea_channel_identity_link(account_id);
