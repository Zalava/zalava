package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.platform.persistence.OptimisticLockConflictException;

public final class JdbcKnowledgeSourceStore implements KnowledgeSourceStore {
  private static final String SOURCE_COLUMNS =
      "id, owner_account_id, display_name, content_type, byte_count, sha256, visibility, processing_state, created_at, updated_at, version";

  private final JdbcClient jdbc;

  public JdbcKnowledgeSourceStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public KnowledgeSource register(KnowledgeSource source) {
    jdbc.sql(
            "insert into knowledge_source (id, owner_account_id, display_name, content_type, byte_count, sha256, visibility, processing_state, created_at, updated_at, version) values (:id, :owner, :name, :type, :bytes, :sha256, :visibility, :state, :created, :updated, :version)")
        .param("id", source.id().value())
        .param("owner", source.owner().accountId().value())
        .param("name", source.displayName())
        .param("type", source.contentType())
        .param("bytes", source.byteCount())
        .param("sha256", source.sha256())
        .param("visibility", source.visibility().name())
        .param("state", source.processingState().name())
        .param("created", Timestamp.from(source.createdAt()))
        .param("updated", Timestamp.from(source.updatedAt()))
        .param("version", source.version())
        .update();
    return source;
  }

  @Override
  public KnowledgeSource save(KnowledgeSource source) {
    int updated =
        jdbc.sql(
                "update knowledge_source set visibility=:visibility, processing_state=:state, updated_at=:updated, version=version+1 where id=:id and version=:expectedVersion")
            .param("id", source.id().value())
            .param("visibility", source.visibility().name())
            .param("state", source.processingState().name())
            .param("updated", Timestamp.from(source.updatedAt()))
            .param("expectedVersion", source.version())
            .update();
    if (updated != 1) throw new OptimisticLockConflictException("Knowledge source", source.id());
    return source.withVersion(source.version() + 1);
  }

  @Override
  public Optional<KnowledgeSource> findById(KnowledgeSourceId id) {
    return jdbc.sql("select " + SOURCE_COLUMNS + " from knowledge_source where id=:id")
        .param("id", id.value())
        .query(this::map)
        .optional();
  }

  @Override
  public List<KnowledgeSource> visibleTo(Actor actor) {
    return jdbc.sql(
            "select "
                + SOURCE_COLUMNS
                + " from knowledge_source where processing_state <> 'DELETED' and (owner_account_id=:owner or visibility='GROUP_SHARED') order by created_at")
        .param("owner", actor.accountId().value())
        .query(this::map)
        .list();
  }

  @Override
  public void delete(KnowledgeSource source) {
    int deleted =
        jdbc.sql("delete from knowledge_source where id=:id and version=:expectedVersion")
            .param("id", source.id().value())
            .param("expectedVersion", source.version())
            .update();
    if (deleted != 1) throw new OptimisticLockConflictException("Knowledge source", source.id());
  }

  private KnowledgeSource map(ResultSet row, int index) throws SQLException {
    return new KnowledgeSource(
        new KnowledgeSourceId(row.getObject("id", java.util.UUID.class)),
        new Actor(new AccountId(row.getObject("owner_account_id", java.util.UUID.class))),
        row.getString("display_name"),
        row.getString("content_type"),
        row.getLong("byte_count"),
        row.getString("sha256"),
        KnowledgeVisibility.valueOf(row.getString("visibility")),
        SourceProcessingState.valueOf(row.getString("processing_state")),
        row.getTimestamp("created_at").toInstant(),
        row.getTimestamp("updated_at").toInstant(),
        row.getLong("version"));
  }
}
