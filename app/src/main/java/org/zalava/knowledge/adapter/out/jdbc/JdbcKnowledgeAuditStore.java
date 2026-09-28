package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeAuditStore;
import org.zalava.knowledge.domain.KnowledgeSourceId;

public final class JdbcKnowledgeAuditStore implements KnowledgeAuditStore {
  private final JdbcClient jdbc;

  public JdbcKnowledgeAuditStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void recordDeletion(KnowledgeSourceId sourceId, Actor actor) {
    Instant recordedAt = Instant.now();
    int updated =
        jdbc.sql(
                "update knowledge_audit set actor_account_id=:actor, recorded_at=:at where source_id=:source and action='HARD_DELETED'")
            .param("source", sourceId.value())
            .param("actor", actor.accountId().value())
            .param("at", Timestamp.from(recordedAt))
            .update();
    if (updated > 0) {
      return;
    }
    jdbc.sql(
            "insert into knowledge_audit (id, source_id, action, actor_account_id, recorded_at) values (:id, :source, 'HARD_DELETED', :actor, :at)")
        .param("id", UUID.randomUUID())
        .param("source", sourceId.value())
        .param("actor", actor.accountId().value())
        .param("at", Timestamp.from(recordedAt))
        .update();
  }
}
