ALTER TABLE knowledge_source DROP CONSTRAINT knowledge_source_processing_state;
ALTER TABLE knowledge_source ADD CONSTRAINT knowledge_source_processing_state
  CHECK (processing_state IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'CANCELLED', 'DELETION_REQUESTED', 'DELETED'));
