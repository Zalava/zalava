package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeEvidenceStore;
import org.zalava.knowledge.domain.KnowledgeEvidence;

public final class JdbcKnowledgeEvidenceStore implements KnowledgeEvidenceStore {
  private static final String SELECT =
      """
      select s.id, d.version, s.display_name, s.content_type,
             left(r.extracted_text, 2000) as excerpt,
             length(r.extracted_text) > 2000 as truncated
      from knowledge_source s
      join knowledge_derivation d on d.source_id = s.id and d.state = 'ACTIVE'
      join knowledge_extraction_record r on r.source_id = d.source_id
           and r.derivation_version = d.version
      where (s.owner_account_id = :actor or s.visibility = 'GROUP_SHARED')
        and s.processing_state in ('READY', 'PROCESSING')
        and r.extracted_text is not null
      """;
  private final JdbcClient jdbc;

  public JdbcKnowledgeEvidenceStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<KnowledgeEvidence> search(Actor actor, String query, int limit) {
    return jdbc.sql(
            SELECT
                + """
        and (r.search_vector @@ websearch_to_tsquery('simple', :query)
          or to_tsvector('simple', s.display_name) @@ websearch_to_tsquery('simple', :query))
        order by s.updated_at desc, s.id
        limit :limit
        """)
        .param("actor", actor.accountId().value())
        .param("query", query)
        .param("limit", limit)
        .query(this::map)
        .list();
  }

  @Override
  public Optional<KnowledgeEvidence> source(Actor actor, UUID sourceId) {
    return jdbc.sql(SELECT + " and s.id = :source")
        .param("actor", actor.accountId().value())
        .param("source", sourceId)
        .query(this::map)
        .optional();
  }

  private KnowledgeEvidence map(ResultSet row, int index) throws SQLException {
    return new KnowledgeEvidence(
        row.getObject("id", UUID.class),
        row.getLong("version"),
        row.getString("display_name"),
        row.getString("content_type"),
        row.getString("excerpt"),
        row.getBoolean("truncated"));
  }
}
