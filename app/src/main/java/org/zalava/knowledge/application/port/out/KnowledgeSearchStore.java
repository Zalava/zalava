package org.zalava.knowledge.application.port.out;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.SourceProcessingState;

/** Native-index candidate lookup; authorization remains with the SEA application layer. */
public interface KnowledgeSearchStore {
  List<Candidate> findCandidates(SearchCriteria criteria);

  record Candidate(KnowledgeSourceId sourceId, long derivationVersion) {}

  /**
   * Candidate criteria. A non-null {@code actor} narrows native candidates to that actor's visible
   * sources before the native limit, so private candidates cannot starve an authorized result. The
   * application layer still rechecks visibility before disclosure.
   */
  record SearchCriteria(
      String query,
      String contentType,
      SourceProcessingState processingState,
      int limit,
      Actor actor) {
    public SearchCriteria(
        String query, String contentType, SourceProcessingState processingState, int limit) {
      this(query, contentType, processingState, limit, null);
    }
  }
}
