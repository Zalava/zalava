package org.zalava.modules.development.application;

import java.time.Clock;
import java.util.Objects;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;

/** The sole boundary that records binary acceptance evidence before installation approval. */
public class DevelopmentCandidateValidationGateway {
  private final DevelopmentRequestStore requests;
  private final DevelopmentCandidateEvaluator evaluator;
  private final Clock clock;

  public DevelopmentCandidateValidationGateway(
      DevelopmentRequestStore requests, DevelopmentCandidateEvaluator evaluator, Clock clock) {
    this.requests = Objects.requireNonNull(requests);
    this.evaluator = Objects.requireNonNull(evaluator);
    this.clock = Objects.requireNonNull(clock);
  }

  public synchronized ModuleDevelopmentRequest submit(
      DevelopmentRequestId requestId, LocalArtifactInspection.InspectedArtifact artifact) {
    ModuleDevelopmentRequest request;
    try {
      request = requests.get(requestId);
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException(
          "Development request is not available for binary validation");
    }
    ModuleDevelopmentRequest submitted =
        requests.save(
            request.recordCandidate(
                new ModuleDevelopmentRequest.CandidateAttempt(
                    request.candidateAttempts().size() + 1,
                    clock.instant(),
                    artifact.path(),
                    artifact.sha256Digest(),
                    null)));
    CandidateEvaluation evaluation = evaluator.evaluate(submitted, artifact);
    return requests.save(submitted.recordEvaluation(evaluation));
  }

  public synchronized Evidence requireAccepted(
      DevelopmentRequestId requestId,
      LocalArtifactInspection.InspectedArtifact artifact,
      String moduleId,
      String version) {
    ModuleDevelopmentRequest request;
    try {
      request = requests.get(requestId);
    } catch (RuntimeException ex) {
      throw new SourceModuleInstallationException(
          "Development request is not available for binary validation");
    }
    if (!request.currentRevision().contract().module().moduleId().equals(moduleId)
        || !request.currentRevision().contract().module().versionPolicy().equals(version)) {
      throw new SourceModuleInstallationException(
          "Development request does not match binary module identity");
    }
    return request.candidateAttempts().stream()
        .filter(attempt -> attempt.sha256Digest().equals(artifact.sha256Digest()))
        .filter(attempt -> attempt.evaluation() != null && attempt.evaluation().accepted())
        .reduce((ignored, latest) -> latest)
        .map(
            attempt ->
                new Evidence(
                    request.id().value(), attempt.number(), attempt.evaluation().decision()))
        .orElseThrow(
            () ->
                new SourceModuleInstallationException(
                    "Binary has no accepted development-candidate evaluation for the supplied request"));
  }

  /**
   * Returns authoritative contract and report only after the universal gateway accepted this
   * digest.
   */
  public synchronized AcceptedCandidate acceptedCandidate(
      DevelopmentRequestId requestId, LocalArtifactInspection.InspectedArtifact artifact) {
    ModuleDevelopmentRequest request = requests.get(requestId);
    Evidence evidence =
        requireAccepted(
            requestId,
            artifact,
            request.currentRevision().contract().module().moduleId(),
            request.currentRevision().contract().module().versionPolicy());
    CandidateEvaluation evaluation =
        request.candidateAttempts().stream()
            .filter(attempt -> attempt.number() == evidence.candidateAttemptNumber())
            .findFirst()
            .orElseThrow()
            .evaluation();
    return new AcceptedCandidate(request.currentRevision().contract(), evaluation);
  }

  public record Evidence(
      String developmentRequestId,
      int candidateAttemptNumber,
      CandidateEvaluation.Decision decision) {
    public Evidence {
      if (developmentRequestId == null || developmentRequestId.isBlank()) {
        throw new IllegalArgumentException("developmentRequestId must not be blank");
      }
      if (candidateAttemptNumber < 1)
        throw new IllegalArgumentException("candidateAttemptNumber must be positive");
      decision = Objects.requireNonNull(decision, "decision must not be null");
      if (decision == CandidateEvaluation.Decision.REJECTED) {
        throw new IllegalArgumentException("rejected candidate evidence is not installable");
      }
    }
  }

  public record AcceptedCandidate(
      org.zalava.modules.development.ModuleDevelopmentContract contract,
      CandidateEvaluation evaluation) {}
}
