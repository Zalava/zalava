package org.zalava.knowledge.adapter.out.jobrunr;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.zalava.knowledge.adapter.in.jobrunr.KnowledgeExtractionHandler;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;

class JobRunrKnowledgeIngestionSchedulerTest {
  @Test
  void enqueuesTheExtractionHandlerWithTheSourceIdentifier() {
    JobScheduler jobScheduler = mock(JobScheduler.class);
    KnowledgeSourceId sourceId = KnowledgeSourceId.create();

    new JobRunrKnowledgeIngestionScheduler(jobScheduler, mock(KnowledgeExtractionHandler.class))
        .enqueue(sourceId);

    verify(jobScheduler)
        .enqueue(
            eq(sourceId.value()),
            org.mockito.ArgumentMatchers.<IocJobLambda<KnowledgeExtractionHandler>>any());
  }

  @Test
  void cancelsTheJobUsingItsOpaqueSourceIdentifier() {
    JobScheduler jobScheduler = mock(JobScheduler.class);
    KnowledgeSourceId sourceId = KnowledgeSourceId.create();

    new JobRunrKnowledgeIngestionScheduler(jobScheduler, mock(KnowledgeExtractionHandler.class))
        .cancel(sourceId);

    verify(jobScheduler).delete(sourceId.value());
  }
}
