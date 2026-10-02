package org.zalava.knowledge.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeEvidenceStore;
import org.zalava.knowledge.domain.KnowledgeEvidence;

/** Actor-owned and explicitly group-shared digital evidence only. */
public final class KnowledgeEvidenceQueries {
  private final KnowledgeEvidenceStore store;

  public KnowledgeEvidenceQueries(KnowledgeEvidenceStore store) {
    this.store = Objects.requireNonNull(store);
  }

  public List<KnowledgeEvidence> search(Actor actor, String query, Integer limit) {
    Objects.requireNonNull(actor);
    if (query == null
        || query.isBlank()
        || query.length() > 256
        || (limit != null && (limit < 1 || limit > 8))) {
      throw new IllegalArgumentException("Invalid knowledge search input");
    }
    return store.search(actor, query.strip(), limit == null ? 5 : limit);
  }

  public Optional<KnowledgeEvidence> source(Actor actor, String id) {
    Objects.requireNonNull(actor);
    if (id == null
        || !id.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
      throw new IllegalArgumentException("Invalid source identifier");
    }
    return store.source(actor, UUID.fromString(id));
  }
}
