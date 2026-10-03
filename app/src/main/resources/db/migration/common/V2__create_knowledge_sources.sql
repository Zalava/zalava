CREATE TABLE knowledge_source (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    byte_count BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    processing_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT knowledge_source_byte_count CHECK (byte_count >= 0),
    CONSTRAINT knowledge_source_visibility CHECK (visibility IN ('PRIVATE', 'GROUP_SHARED')),
    CONSTRAINT knowledge_source_processing_state CHECK (processing_state IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'DELETION_REQUESTED', 'DELETED')),
    CONSTRAINT knowledge_source_owner FOREIGN KEY (owner_account_id) REFERENCES zalava_account(id)
);

CREATE INDEX knowledge_source_owner_visibility_idx
    ON knowledge_source(owner_account_id, visibility, created_at);

CREATE TABLE knowledge_audit (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_account_id UUID NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT knowledge_audit_source_action UNIQUE (source_id, action)
);

CREATE INDEX knowledge_audit_source_idx ON knowledge_audit(source_id, recorded_at);
