CREATE TABLE knowledge_extraction_record (
  source_id UUID NOT NULL,
  derivation_version BIGINT NOT NULL,
  extracted_text CLOB,
  failure_category VARCHAR(64),
  failure_detail VARCHAR(1024),
  PRIMARY KEY (source_id, derivation_version),
  CONSTRAINT knowledge_extraction_record_derivation FOREIGN KEY (source_id, derivation_version)
    REFERENCES knowledge_derivation(source_id, version) ON DELETE CASCADE,
  CONSTRAINT knowledge_extraction_record_outcome CHECK (
    (extracted_text IS NOT NULL AND failure_category IS NULL AND failure_detail IS NULL)
    OR (extracted_text IS NULL AND failure_category IS NOT NULL AND failure_detail IS NOT NULL))
);
