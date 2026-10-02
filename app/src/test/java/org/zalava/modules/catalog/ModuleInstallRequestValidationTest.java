package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.modules.development.CandidateEvaluation;

class ModuleInstallRequestValidationTest {

  @Test
  void rejectsInvalidLocalArtifactDevelopmentValidationCombinations() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                localRequest(
                    " ",
                    1,
                    CandidateEvaluation.Decision.ACCEPTED,
                    LocalArtifactInstallRequest.Status.PENDING))
        .withMessageContaining("developmentRequestId must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                localRequest(
                    "request-1",
                    0,
                    CandidateEvaluation.Decision.ACCEPTED,
                    LocalArtifactInstallRequest.Status.PENDING))
        .withMessageContaining("candidateAttemptNumber must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                localRequest(
                    "request-1",
                    1,
                    CandidateEvaluation.Decision.REJECTED,
                    LocalArtifactInstallRequest.Status.PENDING))
        .withMessageContaining("rejected validation cannot be approved");
  }

  @Test
  void rejectsInvalidReleaseArtifactDevelopmentValidationCombinations() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> releaseRequest(null, 1, CandidateEvaluation.Decision.ACCEPTED))
        .withMessageContaining("release validation without a development request");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> releaseRequest(" ", 1, CandidateEvaluation.Decision.ACCEPTED))
        .withMessageContaining("developmentRequestId must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> releaseRequest("request-1", 1, CandidateEvaluation.Decision.REJECTED))
        .withMessageContaining("rejected validation cannot be approved");
  }

  private static LocalArtifactInstallRequest localRequest(
      String developmentRequestId,
      int candidateAttemptNumber,
      CandidateEvaluation.Decision decision,
      LocalArtifactInstallRequest.Status status) {
    return new LocalArtifactInstallRequest(
        "install-1",
        Instant.parse("2026-09-01T00:00:00Z"),
        module(),
        "/modules/time.jar",
        "sha256:" + "a".repeat(64),
        developmentRequestId,
        candidateAttemptNumber,
        decision,
        status,
        null,
        null);
  }

  private static ModuleReleaseInstallRequest releaseRequest(
      String developmentRequestId,
      int candidateAttemptNumber,
      CandidateEvaluation.Decision decision) {
    return new ModuleReleaseInstallRequest(
        "install-1",
        Instant.parse("2026-09-01T00:00:00Z"),
        URI.create("https://example.test/releases.yaml"),
        module(),
        "/modules/time.jar",
        "sha256:" + "a".repeat(64),
        "releases",
        developmentRequestId,
        candidateAttemptNumber,
        decision,
        ModuleReleaseInstallRequest.Status.PENDING,
        null,
        null);
  }

  private static SourceModuleIndex.Module module() {
    return new SourceModuleIndex.Module(
        "zalava-module-time",
        "1.0.0",
        "Time",
        "Time provider",
        URI.create("https://example.test/support"),
        new SourceModuleIndex.Artifact("org.example", "zalava-module-time", "1.0.0"),
        new SourceModuleIndex.Source(URI.create("https://example.test/time.git"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }
}
