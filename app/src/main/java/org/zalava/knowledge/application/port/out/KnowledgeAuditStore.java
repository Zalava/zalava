package org.zalava.knowledge.application.port.out;

import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** Retains redacted lifecycle evidence after the authoritative source is deleted. */
public interface KnowledgeAuditStore {
  void recordDeletion(KnowledgeSourceId sourceId, Actor actor);
}
