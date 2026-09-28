ALTER TABLE knowledge_extraction_record
  ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', coalesce(extracted_text, ''))) STORED;

CREATE INDEX knowledge_extraction_record_search_vector_idx
  ON knowledge_extraction_record USING GIN (search_vector);
