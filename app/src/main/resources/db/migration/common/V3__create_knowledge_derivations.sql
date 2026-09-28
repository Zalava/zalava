CREATE TABLE knowledge_derivation (
    source_id UUID NOT NULL,
    version BIGINT NOT NULL,
    processor_id VARCHAR(255) NOT NULL,
    processor_version VARCHAR(255) NOT NULL,
    state VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_id, version),
    CONSTRAINT knowledge_derivation_state CHECK (state IN ('CANDIDATE', 'ACTIVE', 'FAILED', 'REPLACED'))
);

CREATE INDEX knowledge_derivation_source_state_idx
    ON knowledge_derivation(source_id, state, version);
