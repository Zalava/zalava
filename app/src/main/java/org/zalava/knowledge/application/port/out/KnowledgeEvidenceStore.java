package org.zalava.knowledge.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.domain.KnowledgeEvidence;

/** Visibility and active extraction are resolved together before any evidence leaves storage. */
public interface KnowledgeEvidenceStore {
  List<KnowledgeEvidence> search(Actor actor, String query, int limit);

  Optional<KnowledgeEvidence> source(Actor actor, UUID sourceId);
}
