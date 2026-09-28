package org.zalava.knowledge.adapter.out.jobrunr;

import java.util.UUID;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;
import org.zalava.knowledge.adapter.in.jobrunr.KnowledgeExtractionHandler;
import org.zalava.knowledge.application.port.out.KnowledgeIngestionScheduler;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** Uses the source id as the durable job key, making enqueue/cancel idempotent. */
@Component
public final class JobRunrKnowledgeIngestionScheduler implements KnowledgeIngestionScheduler {
  private final JobScheduler scheduler;
  private final KnowledgeExtractionHandler handler;

  public JobRunrKnowledgeIngestionScheduler(
      JobScheduler scheduler, KnowledgeExtractionHandler handler) {
    this.scheduler = scheduler;
    this.handler = handler;
  }

  @Override
  public void enqueue(KnowledgeSourceId sourceId) {
    UUID jobId = sourceId.value();
    scheduler.<KnowledgeExtractionHandler>enqueue(jobId, job -> job.extract(jobId.toString()));
  }

  @Override
  public void cancel(KnowledgeSourceId sourceId) {
    scheduler.delete(sourceId.value());
  }
}
