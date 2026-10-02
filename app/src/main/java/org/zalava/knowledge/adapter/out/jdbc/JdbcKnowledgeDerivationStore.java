package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.platform.persistence.OptimisticLockConflictException;

public final class JdbcKnowledgeDerivationStore implements KnowledgeDerivationStore {
  private static final String DERIVATION_COLUMNS =
      "source_id, version, processor_id, processor_version, state, recorded_at, persistence_version";

  private final JdbcClient jdbc;

  public JdbcKnowledgeDerivationStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public KnowledgeDerivation record(KnowledgeDerivation derivation) {
    jdbc.sql(
            "insert into knowledge_derivation (source_id, version, processor_id, processor_version, state, recorded_at, persistence_version) values (:source, :version, :processor, :processorVersion, :state, :recorded, :persistenceVersion)")
        .param("source", derivation.sourceId().value())
        .param("version", derivation.version())
        .param("processor", derivation.processorId())
        .param("processorVersion", derivation.processorVersion())
        .param("state", derivation.state().name())
        .param("recorded", Timestamp.from(derivation.recordedAt()))
        .param("persistenceVersion", derivation.persistenceVersion())
        .update();
    return derivation;
  }

  @Override
  public KnowledgeDerivation save(KnowledgeDerivation derivation) {
    int updated =
        jdbc.sql(
                "update knowledge_derivation set state=:state, recorded_at=:recorded, persistence_version=persistence_version+1 where source_id=:source and version=:version and persistence_version=:expectedVersion")
            .param("source", derivation.sourceId().value())
            .param("version", derivation.version())
            .param("state", derivation.state().name())
            .param("recorded", Timestamp.from(derivation.recordedAt()))
            .param("expectedVersion", derivation.persistenceVersion())
            .update();
    if (updated != 1)
      throw new OptimisticLockConflictException(
          "Knowledge derivation", derivation.sourceId() + ":" + derivation.version());
    return derivation.withPersistenceVersion(derivation.persistenceVersion() + 1);
  }

  @Override
  public Optional<KnowledgeDerivation> active(KnowledgeSourceId sourceId) {
    return jdbc.sql(
            "select "
                + DERIVATION_COLUMNS
                + " from knowledge_derivation where source_id=:source and state='ACTIVE'")
        .param("source", sourceId.value())
        .query(this::map)
        .optional();
  }

  @Override
  public List<KnowledgeDerivation> findBySourceId(KnowledgeSourceId sourceId) {
    return jdbc.sql(
            "select "
                + DERIVATION_COLUMNS
                + " from knowledge_derivation where source_id=:source order by version")
        .param("source", sourceId.value())
        .query(this::map)
        .list();
  }

  @Override
  public void deleteBySourceId(KnowledgeSourceId sourceId) {
    jdbc.sql("delete from knowledge_derivation where source_id=:source")
        .param("source", sourceId.value())
        .update();
  }

  private KnowledgeDerivation map(ResultSet row, int index) throws SQLException {
    return new KnowledgeDerivation(
        new KnowledgeSourceId(row.getObject("source_id", java.util.UUID.class)),
        row.getLong("version"),
        row.getString("processor_id"),
        row.getString("processor_version"),
        DerivationState.valueOf(row.getString("state")),
        row.getTimestamp("recorded_at").toInstant(),
        row.getLong("persistence_version"));
  }
}
