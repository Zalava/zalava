package org.zalava.capabilities.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.CapabilityGapClassification;
import org.zalava.capabilities.discovery.CapabilityGapEvidence;
import org.zalava.capabilities.discovery.application.port.in.CapabilityGapEvidenceQueries;
import org.zalava.capabilities.discovery.application.port.out.CapabilityGapEvidenceStore;

class DefaultCapabilityGapEvidenceQueriesTest {

  @Test
  void delegatesToTheBoundedEvidenceStore() {
    CapabilityGapEvidence evidence =
        new CapabilityGapEvidence(
            "sha256:" + "a".repeat(64),
            Instant.parse("2026-09-15T10:15:30Z"),
            0,
            CapabilityGapClassification.NO_MATCH,
            "detail",
            List.of());
    CapabilityGapEvidenceStore store =
        new CapabilityGapEvidenceStore() {
          @Override
          public void save(CapabilityGapEvidence value) {}

          @Override
          public List<CapabilityGapEvidence> recent(int limit) {
            return List.of(evidence);
          }
        };

    assertThat(new DefaultCapabilityGapEvidenceQueries(store).recent(5)).containsExactly(evidence);
  }

  @Test
  void rejectsLimitsOutsideTheBoundedRange() {
    DefaultCapabilityGapEvidenceQueries queries =
        new DefaultCapabilityGapEvidenceQueries(
            new CapabilityGapEvidenceStore() {
              @Override
              public void save(CapabilityGapEvidence value) {}

              @Override
              public List<CapabilityGapEvidence> recent(int limit) {
                return List.of();
              }
            });

    assertThatThrownBy(() -> queries.recent(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.recent(CapabilityGapEvidenceQueries.MAX_RESULTS + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
