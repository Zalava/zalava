package org.zalava.knowledge.application.port.out;

import org.zalava.knowledge.domain.KnowledgeSourceId;

/** Durable execution boundary; JobRunr is an adapter detail. */
public interface KnowledgeIngestionScheduler {
  void enqueue(KnowledgeSourceId sourceId);

  void cancel(KnowledgeSourceId sourceId);
}
