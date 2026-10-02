package org.zalava.knowledge.adapter.out.transaction;

import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.*;
import org.zalava.knowledge.domain.*;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;

public class TransactionalKnowledgeSourceLifecycle extends KnowledgeSourceLifecycle {
  @Override
  @Transactional
  public KnowledgeSource changeVisibility(
      Actor actor, KnowledgeSourceId id, KnowledgeVisibility visibility) {
    return super.changeVisibility(actor, id, visibility);
  }

  @Override
  @Transactional
  public KnowledgeSource register(
      Actor actor, String displayName, String contentType, byte[] original) {
    return super.register(actor, displayName, contentType, original);
  }

  @Override
  @Transactional
  public void cancelReprocessing(Actor actor, KnowledgeSourceId id) {
    super.cancelReprocessing(actor, id);
  }

  @Override
  @Transactional
  public KnowledgeDerivation beginReprocessing(
      Actor actor, KnowledgeSourceId id, String processorId, String processorVersion) {
    return super.beginReprocessing(actor, id, processorId, processorVersion);
  }

  public TransactionalKnowledgeSourceLifecycle(
      KnowledgeSourceStore sources,
      KnowledgeDerivationStore derivations,
      KnowledgeBlobStore blobs,
      KnowledgeAuditStore audit,
      Clock clock,
      OperationalMetrics metrics) {
    super(sources, derivations, blobs, audit, clock, metrics);
  }

  @Override
  @Transactional(readOnly = true)
  public KnowledgeSource requireOwned(Actor actor, KnowledgeSourceId id) {
    return super.requireOwned(actor, id);
  }

  @Override
  @Transactional
  public void completeReprocessing(Actor actor, KnowledgeDerivation candidate, boolean successful) {
    super.completeReprocessing(actor, candidate, successful);
  }

  @Override
  @Transactional
  public void hardDelete(Actor actor, KnowledgeSourceId id) {
    super.hardDelete(actor, id);
  }
}
