package org.zalava.catalog;

import java.time.Instant;
import org.zalava.development.CandidateEvaluation;

public record LocalArtifactInstallRequest(
    String requestId,
    Instant createdAt,
    SourceModuleIndex.Module module,
    String artifactPath,
    String artifactDigest,
    String developmentRequestId,
    int candidateAttemptNumber,
    CandidateEvaluation.Decision validationDecision,
    Status status,
    Instant decidedAt,
    String message) {
  public LocalArtifactInstallRequest {
    if (developmentRequestId != null && developmentRequestId.isBlank())
      throw new IllegalArgumentException("developmentRequestId must not be blank when supplied");
    if (developmentRequestId == null && candidateAttemptNumber != 0)
      throw new IllegalArgumentException(
          "local release validation without a development request must not have a candidate attempt");
    if (developmentRequestId != null && candidateAttemptNumber < 1)
      throw new IllegalArgumentException(
          "candidateAttemptNumber must be positive when a development request is supplied");
    if (validationDecision == null && developmentRequestId == null && candidateAttemptNumber == 0) {
      validationDecision = CandidateEvaluation.Decision.ACCEPTED;
    }
    validationDecision =
        java.util.Objects.requireNonNull(validationDecision, "validationDecision must not be null");
    if (validationDecision == CandidateEvaluation.Decision.REJECTED)
      throw new IllegalArgumentException("rejected validation cannot be approved");
  }

  public enum Status {
    PENDING,
    ALLOWED,
    DENIED,
    SUCCEEDED,
    FAILED
  }
}
