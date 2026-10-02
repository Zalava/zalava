package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;

public final class JdbcKnowledgeExtractionRecordStore implements KnowledgeExtractionRecordStore {
  private final JdbcClient jdbc;

  public JdbcKnowledgeExtractionRecordStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(KnowledgeExtractionRecord record) {
    jdbc.sql(
            "insert into knowledge_extraction_record (source_id, derivation_version, extracted_text, failure_category, failure_detail) values (:source, :version, :text, :category, :detail)")
        .param("source", record.sourceId().value())
        .param("version", record.derivationVersion())
        .param("text", record.text())
        .param(
            "category", record.failureCategory() == null ? null : record.failureCategory().name())
        .param("detail", record.failureDetail())
        .update();
  }

  @Override
  public Optional<KnowledgeExtractionRecord> find(
      KnowledgeSourceId sourceId, long derivationVersion) {
    return jdbc.sql(
            "select source_id, derivation_version, extracted_text, failure_category, failure_detail from knowledge_extraction_record where source_id=:source and derivation_version=:version")
        .param("source", sourceId.value())
        .param("version", derivationVersion)
        .query(this::map)
        .optional();
  }

  private KnowledgeExtractionRecord map(ResultSet row, int index) throws SQLException {
    String category = row.getString("failure_category");
    return new KnowledgeExtractionRecord(
        new KnowledgeSourceId(row.getObject("source_id", java.util.UUID.class)),
        row.getLong("derivation_version"),
        row.getString("extracted_text"),
        category == null ? null : ContentExtractionFailureCategory.valueOf(category),
        row.getString("failure_detail"));
  }
}
