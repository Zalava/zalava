package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.zalava.development.adapter.out.filesystem.FileSystemDevelopmentRequestStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class FileSystemDevelopmentRequestStoreTest {

  @TempDir Path workspace;

  @Test
  void persistsAndReloadsTheAuthoritativeContractAndRevisionHistory() throws Exception {
    FileSystemDevelopmentRequestStore store =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace));
    ModuleDevelopmentRequest saved =
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId("request-1"),
            Instant.parse("2026-07-25T12:00:00Z"),
            DevelopmentRequestStatus.IN_DEVELOPMENT,
            List.of(
                new ModuleDevelopmentRequest.Revision(
                    1,
                    Instant.parse("2026-07-25T12:00:00Z"),
                    "Initial request",
                    ModuleDevelopmentRequestTest.contract("0.1.0")),
                new ModuleDevelopmentRequest.Revision(
                    2,
                    Instant.parse("2026-07-25T12:01:00Z"),
                    "Add output",
                    ModuleDevelopmentRequestTest.contract("0.2.0"))));
    store.save(saved);

    FileSystemDevelopmentRequestStore reloaded =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace));

    assertThat(reloaded.get(new DevelopmentRequestId("request-1"))).isEqualTo(saved);
  }

  @Test
  void persistsCandidateAttemptAndEvidenceReport() throws Exception {
    FileSystemDevelopmentRequestStore store =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace));
    ModuleDevelopmentRequest saved =
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId("request-candidate"),
            Instant.parse("2026-07-25T12:00:00Z"),
            DevelopmentRequestStatus.READY_TO_INSTALL,
            List.of(
                new ModuleDevelopmentRequest.Revision(
                    1,
                    Instant.parse("2026-07-25T12:00:00Z"),
                    "Initial request",
                    ModuleDevelopmentRequestTest.contract("0.1.0"))),
            List.of(
                new ModuleDevelopmentRequest.CandidateAttempt(
                    1,
                    Instant.parse("2026-07-25T12:02:00Z"),
                    "/trusted/example.jar",
                    "sha256:" + "a".repeat(64),
                    new CandidateEvaluation(
                        true,
                        Instant.parse("2026-07-25T12:03:00Z"),
                        List.of("Loaded module"),
                        "{\"decision\":\"ACCEPTED\"}",
                        "# Accepted",
                        List.of()))));
    store.save(saved);

    ModuleDevelopmentRequest reloaded =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace))
            .get(new DevelopmentRequestId("request-candidate"));

    assertThat(reloaded).isEqualTo(saved);
  }

  @Test
  void reloadsLegacyRevisionWhileRetainingItsConcreteCurrentRevision() throws Exception {
    FileSystemDevelopmentRequestStore store =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace));
    ModuleDevelopmentRequest saved =
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId("legacy-request"),
            Instant.parse("2026-07-25T12:00:00Z"),
            DevelopmentRequestStatus.REVISION_REQUIRED,
            List.of(
                new ModuleDevelopmentRequest.Revision(
                    1,
                    Instant.parse("2026-07-25T12:00:00Z"),
                    "Legacy request",
                    ModuleDevelopmentRequestTest.contract("semver")),
                new ModuleDevelopmentRequest.Revision(
                    2,
                    Instant.parse("2026-07-25T12:01:00Z"),
                    "Correct version",
                    ModuleDevelopmentRequestTest.contract("1.0.0"))));
    store.save(saved);

    ModuleDevelopmentRequest reloaded =
        new FileSystemDevelopmentRequestStore(new FileSystemResource(workspace))
            .get(new DevelopmentRequestId("legacy-request"));

    assertThat(reloaded.currentRevision().contract().module().versionPolicy()).isEqualTo("1.0.0");
    assertThat(reloaded.revisions().getFirst().contract().module().versionPolicy())
        .isEqualTo("semver");
  }
}
