CREATE INDEX knowledge_source_display_name_search_idx
ON knowledge_source USING GIN (to_tsvector('simple', display_name));
