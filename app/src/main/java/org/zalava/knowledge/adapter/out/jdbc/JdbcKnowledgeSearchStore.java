package org.zalava.knowledge.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.knowledge.application.port.out.KnowledgeSearchStore;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** PostgreSQL full-text candidates only; callers must apply Zalava visibility before disclosure. */
public final class JdbcKnowledgeSearchStore implements KnowledgeSearchStore {
  private final JdbcClient jdbc;

  public JdbcKnowledgeSearchStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Candidate> findCandidates(SearchCriteria criteria) {
    String authorization =
        criteria.actor() == null
            ? ""
            : " and (source.owner_account_id = :actor or source.visibility = 'GROUP_SHARED') ";
    var statement =
        jdbc.sql(
                """
                select record.source_id, record.derivation_version
                from knowledge_extraction_record record
                join knowledge_derivation derivation
                  on derivation.source_id = record.source_id
                 and derivation.version = record.derivation_version
                 and derivation.state = 'ACTIVE'
                join knowledge_source source on source.id = record.source_id
                where (
                  record.search_vector @@ websearch_to_tsquery('simple', :query)
                  or to_tsvector('simple', source.display_name) @@ websearch_to_tsquery('simple', :query)
                )
                """
                    + authorization
                    + """
                and source.content_type = coalesce(:contentType, source.content_type)
                and source.processing_state = coalesce(:processingState, source.processing_state)
                and source.processing_state <> 'DELETED'
                order by source.updated_at desc, source.id
                limit :limit
                """)
            .param("query", criteria.query())
            .param("contentType", criteria.contentType())
            .param(
                "processingState",
                criteria.processingState() == null ? null : criteria.processingState().name())
            .param("limit", criteria.limit());
    if (criteria.actor() != null) {
      statement = statement.param("actor", criteria.actor().accountId().value());
    }
    return statement.query(this::map).list();
  }

  private Candidate map(ResultSet row, int index) throws SQLException {
    return new Candidate(
        new KnowledgeSourceId(row.getObject("source_id", java.util.UUID.class)),
        row.getLong("derivation_version"));
  }
}
