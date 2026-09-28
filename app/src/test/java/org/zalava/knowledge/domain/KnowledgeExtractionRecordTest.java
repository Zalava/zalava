package org.zalava.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.zalava.content.ContentExtractionFailureCategory;

class KnowledgeExtractionRecordTest {
  private final KnowledgeDerivation derivation =
      new KnowledgeDerivation(
          KnowledgeSourceId.create(),
          1,
          "tika",
          "4.0.0",
          DerivationState.CANDIDATE,
          Instant.EPOCH,
          0);

  @Test
  void rejectsAmbiguousOrUnboundedPersistedOutcomes() {
    assertThatThrownBy(
            () -> new KnowledgeExtractionRecord(derivation.sourceId(), 1, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new KnowledgeExtractionRecord(
                    derivation.sourceId(),
                    1,
                    "text",
                    ContentExtractionFailureCategory.INTERNAL,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                KnowledgeExtractionRecord.failed(
                    derivation, ContentExtractionFailureCategory.INTERNAL, "x".repeat(1025)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createsExclusiveSuccessfulAndFailedRecords() {
    org.assertj.core.api.Assertions.assertThat(
            KnowledgeExtractionRecord.succeeded(derivation, "text").text())
        .isEqualTo("text");
    org.assertj.core.api.Assertions.assertThat(
            KnowledgeExtractionRecord.failed(
                    derivation, ContentExtractionFailureCategory.TIMED_OUT, "Timed out")
                .failureCategory())
        .isEqualTo(ContentExtractionFailureCategory.TIMED_OUT);
  }
}
