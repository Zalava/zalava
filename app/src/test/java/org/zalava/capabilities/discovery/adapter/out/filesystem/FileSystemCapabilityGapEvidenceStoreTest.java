package org.zalava.capabilities.discovery.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.capabilities.discovery.CapabilityGapClassification;
import org.zalava.capabilities.discovery.CapabilityGapEvidence;
import org.zalava.capabilities.discovery.CapabilityGapEvidence.RankedCandidate;

class FileSystemCapabilityGapEvidenceStoreTest {

  @TempDir Path workspace;

  @Test
  void persistsAndReloadsEvidenceWithoutRawQueryText() {
    FileSystemCapabilityGapEvidenceStore store =
        new FileSystemCapabilityGapEvidenceStore(workspace);
    CapabilityGapEvidence evidence =
        new CapabilityGapEvidence(
            "sha256:" + "b".repeat(64),
            Instant.parse("2026-09-15T10:15:30Z"),
            0,
            CapabilityGapClassification.WEAK_MATCH,
            "recommendation only",
            List.of(
                new RankedCandidate(
                    "zalava-module-weather", "1.0.0", "c".repeat(64), 1, "moduleId")));

    store.save(evidence);

    FileSystemCapabilityGapEvidenceStore reloaded =
        new FileSystemCapabilityGapEvidenceStore(workspace);
    assertThat(reloaded.recent(10)).containsExactly(evidence);
  }

  @Test
  void returnsNewestEvidenceFirstAndHonorsTheLimit() {
    FileSystemCapabilityGapEvidenceStore store =
        new FileSystemCapabilityGapEvidenceStore(workspace);
    store.save(evidence("sha256:" + "1".repeat(64), CapabilityGapClassification.NO_MATCH));
    store.save(evidence("sha256:" + "2".repeat(64), CapabilityGapClassification.WEAK_MATCH));
    store.save(evidence("sha256:" + "3".repeat(64), CapabilityGapClassification.UNAVAILABLE));

    assertThat(store.recent(10))
        .extracting(CapabilityGapEvidence::queryDigest)
        .containsExactly(
            "sha256:" + "3".repeat(64), "sha256:" + "2".repeat(64), "sha256:" + "1".repeat(64));
    assertThat(store.recent(2)).hasSize(2);
    assertThat(store.recent(2).getFirst().queryDigest()).isEqualTo("sha256:" + "3".repeat(64));
  }

  @Test
  void returnsEmptyEvidenceBeforeAnythingIsPersisted() {
    assertThat(new FileSystemCapabilityGapEvidenceStore(workspace).recent(10)).isEmpty();
  }

  @Test
  void boundsPersistedHistory() {
    FileSystemCapabilityGapEvidenceStore store =
        new FileSystemCapabilityGapEvidenceStore(workspace);
    for (int index = 0; index < FileSystemCapabilityGapEvidenceStore.MAX_RECORDS + 5; index++) {
      store.save(
          evidence(
              "sha256:" + String.format("%064d", index), CapabilityGapClassification.NO_MATCH));
    }

    assertThat(store.recent(FileSystemCapabilityGapEvidenceStore.MAX_RECORDS))
        .hasSize(FileSystemCapabilityGapEvidenceStore.MAX_RECORDS);
  }

  private static CapabilityGapEvidence evidence(
      String digest, CapabilityGapClassification classification) {
    return new CapabilityGapEvidence(
        digest, Instant.parse("2026-09-15T10:15:30Z"), 0, classification, "detail", List.of());
  }
}
