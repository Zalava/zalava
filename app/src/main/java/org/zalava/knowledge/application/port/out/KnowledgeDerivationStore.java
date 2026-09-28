package org.zalava.knowledge.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** SEA-owned derivation lifecycle boundary; processors submit no direct storage mutation. */
public interface KnowledgeDerivationStore {
  KnowledgeDerivation record(KnowledgeDerivation derivation);

  KnowledgeDerivation save(KnowledgeDerivation derivation);

  Optional<KnowledgeDerivation> active(KnowledgeSourceId sourceId);

  List<KnowledgeDerivation> findBySourceId(KnowledgeSourceId sourceId);

  void deleteBySourceId(KnowledgeSourceId sourceId);
}
