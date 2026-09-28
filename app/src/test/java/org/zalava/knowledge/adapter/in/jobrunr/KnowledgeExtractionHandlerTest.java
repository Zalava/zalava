package org.zalava.knowledge.adapter.in.jobrunr;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.zalava.knowledge.application.KnowledgeExtractionJob;
import org.junit.jupiter.api.Test;

class KnowledgeExtractionHandlerTest {
  @Test
  void delegatesThePersistedSourceIdToTheApplicationUseCase() {
    KnowledgeExtractionJob extraction = mock(KnowledgeExtractionJob.class);

    new KnowledgeExtractionHandler(extraction).extract("source-id");

    verify(extraction).extract("source-id");
  }
}
