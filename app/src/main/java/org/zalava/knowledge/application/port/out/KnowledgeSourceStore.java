package org.zalava.knowledge.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/**
 * Persistence boundary for SEA-owned metadata; source bytes remain in the separate blob boundary.
 */
public interface KnowledgeSourceStore {
  KnowledgeSource register(KnowledgeSource source);

  KnowledgeSource save(KnowledgeSource source);

  Optional<KnowledgeSource> findById(KnowledgeSourceId id);

  List<KnowledgeSource> visibleTo(Actor actor);

  void delete(KnowledgeSource source);
}
