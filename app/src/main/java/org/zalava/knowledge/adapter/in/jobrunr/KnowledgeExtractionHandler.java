package org.zalava.knowledge.adapter.in.jobrunr;

import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;
import org.zalava.knowledge.application.KnowledgeExtractionJob;

/** JobRunr inbound adapter; the application use case remains framework-free. */
@Component
public final class KnowledgeExtractionHandler {
  private static final int RETRIES = 2;
  private final KnowledgeExtractionJob extraction;

  public KnowledgeExtractionHandler(KnowledgeExtractionJob extraction) {
    this.extraction = extraction;
  }

  @Job(name = "Extract knowledge source %0", retries = RETRIES)
  public void extract(String sourceId) {
    extraction.extract(sourceId);
  }
}
