package org.zalava.knowledge.adapter.in.jobrunr;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.zalava.knowledge.application.KnowledgeExtractionJob;

class KnowledgeExtractionHandlerTest {
  @Test
  void delegatesThePersistedSourceIdToTheApplicationUseCase() {
    KnowledgeExtractionJob extraction = mock(KnowledgeExtractionJob.class);

    new KnowledgeExtractionHandler(extraction).extract("source-id");

    verify(extraction).extract("source-id");
  }
}
