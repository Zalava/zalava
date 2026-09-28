package org.zalava.catalog;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.zalava.development.CandidateEvaluation;

/** A prepared immutable binary release awaiting an explicit installation decision. */
public record ModuleReleaseInstallRequest(
    String requestId,
    Instant createdAt,
    URI manifestUri,
    SourceModuleIndex.Module module,
    String artifactPath,
    String artifactDigest,
    List<RuntimeArtifact> runtimeArtifacts,
    boolean artifactBundle,
    String repositoryId,
    String developmentRequestId,
    int candidateAttemptNumber,
    CandidateEvaluation.Decision validationDecision,
    Status status,
    Instant decidedAt,
    String message) {
  public ModuleReleaseInstallRequest(
      String requestId,
      Instant createdAt,
      URI manifestUri,
      SourceModuleIndex.Module module,
      String artifactPath,
      String artifactDigest,
      String repositoryId,
      String developmentRequestId,
      int candidateAttemptNumber,
      CandidateEvaluation.Decision validationDecision,
      Status status,
      Instant decidedAt,
      String message) {
    this(
        requestId,
        createdAt,
        manifestUri,
        module,
        artifactPath,
        artifactDigest,
        List.of(),
        false,
        repositoryId,
        developmentRequestId,
        candidateAttemptNumber,
        validationDecision,
        status,
        decidedAt,
        message);
  }

  public record RuntimeArtifact(
      SourceModuleIndex.Artifact artifact, String artifactPath, String artifactDigest) {}

  public ModuleReleaseInstallRequest {
    runtimeArtifacts = runtimeArtifacts == null ? List.of() : List.copyOf(runtimeArtifacts);
    if (developmentRequestId != null && developmentRequestId.isBlank())
      throw new IllegalArgumentException("developmentRequestId must not be blank when supplied");
    if (developmentRequestId == null && candidateAttemptNumber != 0)
      throw new IllegalArgumentException(
          "release validation without a development request must not have a candidate attempt");
    if (developmentRequestId != null && candidateAttemptNumber < 1)
      throw new IllegalArgumentException(
          "candidateAttemptNumber must be positive when a development request is supplied");
    validationDecision =
        java.util.Objects.requireNonNull(validationDecision, "validationDecision must not be null");
    if (validationDecision == CandidateEvaluation.Decision.REJECTED)
      throw new IllegalArgumentException("rejected validation cannot be approved");
  }

  public enum Status {
    PENDING,
    DENIED,
    SUCCEEDED,
    FAILED
  }
}
