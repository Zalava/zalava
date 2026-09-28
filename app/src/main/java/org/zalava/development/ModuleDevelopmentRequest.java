package org.zalava.development;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ModuleDevelopmentRequest(
    DevelopmentRequestId id,
    Instant createdAt,
    DevelopmentRequestStatus status,
    List<Revision> revisions,
    List<CandidateAttempt> candidateAttempts) {

  public ModuleDevelopmentRequest(
      DevelopmentRequestId id,
      Instant createdAt,
      DevelopmentRequestStatus status,
      List<Revision> revisions) {
    this(id, createdAt, status, revisions, List.of());
  }

  public ModuleDevelopmentRequest {
    id = Objects.requireNonNull(id, "id must not be null");
    createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    revisions =
        revisions == null || revisions.isEmpty()
            ? fail("revisions must not be empty")
            : List.copyOf(revisions);
    candidateAttempts = candidateAttempts == null ? List.of() : List.copyOf(candidateAttempts);
    for (int index = 0; index < revisions.size(); index++) {
      if (revisions.get(index).number() != index + 1)
        throw new IllegalArgumentException("revision numbers must be contiguous from one");
    }
  }

  public Revision currentRevision() {
    return revisions.getLast();
  }

  public ModuleDevelopmentRequest revise(
      ModuleDevelopmentContract contract, String reason, Instant revisedAt) {
    if (!canRevise(status))
      throw new DevelopmentRequestException("Request cannot be revised while " + status);
    List<Revision> updated = new java.util.ArrayList<>(revisions);
    updated.add(new Revision(revisions.size() + 1, revisedAt, reason, contract));
    DevelopmentRequestStatus revisedStatus =
        status == DevelopmentRequestStatus.REVISION_REQUIRED
            ? DevelopmentRequestStatus.IN_DEVELOPMENT
            : status;
    return new ModuleDevelopmentRequest(id, createdAt, revisedStatus, updated, candidateAttempts);
  }

  public ModuleDevelopmentRequest transitionTo(DevelopmentRequestStatus target) {
    Objects.requireNonNull(target, "target must not be null");
    if (target == status) return this;
    if (!isAllowed(status, target))
      throw new DevelopmentRequestException(
          "Invalid lifecycle transition: " + status + " -> " + target);
    return new ModuleDevelopmentRequest(id, createdAt, target, revisions, candidateAttempts);
  }

  public ModuleDevelopmentRequest recordCandidate(CandidateAttempt attempt) {
    Objects.requireNonNull(attempt, "attempt must not be null");
    if (status != DevelopmentRequestStatus.IN_DEVELOPMENT) {
      throw new DevelopmentRequestException("Candidate can only be submitted while IN_DEVELOPMENT");
    }
    if (attempt.number() != candidateAttempts.size() + 1) {
      throw new DevelopmentRequestException(
          "candidate attempt numbers must be contiguous from one");
    }
    List<CandidateAttempt> updated = new java.util.ArrayList<>(candidateAttempts);
    updated.add(attempt);
    return new ModuleDevelopmentRequest(
        id, createdAt, DevelopmentRequestStatus.CANDIDATE_SUBMITTED, revisions, updated);
  }

  public ModuleDevelopmentRequest recordEvaluation(CandidateEvaluation evaluation) {
    Objects.requireNonNull(evaluation, "evaluation must not be null");
    if (status != DevelopmentRequestStatus.CANDIDATE_SUBMITTED) {
      throw new DevelopmentRequestException("Candidate can only be evaluated after submission");
    }
    List<CandidateAttempt> updated = new java.util.ArrayList<>(candidateAttempts);
    if (updated.isEmpty())
      throw new DevelopmentRequestException("Candidate evaluation requires a submitted attempt");
    updated.set(updated.size() - 1, updated.getLast().withEvaluation(evaluation));
    DevelopmentRequestStatus result =
        evaluation.accepted()
            ? DevelopmentRequestStatus.READY_TO_INSTALL
            : DevelopmentRequestStatus.REVISION_REQUIRED;
    return new ModuleDevelopmentRequest(id, createdAt, result, revisions, updated);
  }

  public record Revision(
      int number, Instant revisedAt, String reason, ModuleDevelopmentContract contract) {
    public Revision {
      if (number < 1) throw new IllegalArgumentException("revision number must be positive");
      revisedAt = Objects.requireNonNull(revisedAt, "revisedAt must not be null");
      if (reason == null || reason.isBlank())
        throw new IllegalArgumentException("revision reason must not be blank");
      contract = Objects.requireNonNull(contract, "contract must not be null");
    }
  }

  public record CandidateAttempt(
      int number,
      Instant submittedAt,
      String artifactPath,
      String sha256Digest,
      CandidateEvaluation evaluation) {
    public CandidateAttempt {
      if (number < 1)
        throw new IllegalArgumentException("candidate attempt number must be positive");
      submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
      if (artifactPath == null || artifactPath.isBlank())
        throw new IllegalArgumentException("artifactPath must not be blank");
      if (sha256Digest == null || !sha256Digest.matches("sha256:[0-9a-f]{64}")) {
        throw new IllegalArgumentException("sha256Digest must be a SHA-256 digest");
      }
    }

    CandidateAttempt withEvaluation(CandidateEvaluation result) {
      return new CandidateAttempt(number, submittedAt, artifactPath, sha256Digest, result);
    }
  }

  private static boolean canRevise(DevelopmentRequestStatus status) {
    return switch (status) {
      case PREPARED, EXPORTED, IN_DEVELOPMENT, REVISION_REQUIRED -> true;
      default -> false;
    };
  }

  private static boolean isAllowed(DevelopmentRequestStatus from, DevelopmentRequestStatus to) {
    return switch (from) {
      case PREPARED -> to == DevelopmentRequestStatus.EXPORTED || terminal(to);
      case EXPORTED -> to == DevelopmentRequestStatus.IN_DEVELOPMENT || terminal(to);
      case IN_DEVELOPMENT -> to == DevelopmentRequestStatus.CANDIDATE_SUBMITTED || terminal(to);
      case CANDIDATE_SUBMITTED -> to == DevelopmentRequestStatus.VALIDATING || terminal(to);
      case VALIDATING ->
          to == DevelopmentRequestStatus.REVISION_REQUIRED
              || to == DevelopmentRequestStatus.READY_TO_INSTALL
              || to == DevelopmentRequestStatus.FAILED;
      case REVISION_REQUIRED -> to == DevelopmentRequestStatus.IN_DEVELOPMENT || terminal(to);
      case READY_TO_INSTALL -> to == DevelopmentRequestStatus.INSTALLED || terminal(to);
      case INSTALLED, CANCELLED, FAILED -> false;
    };
  }

  private static boolean terminal(DevelopmentRequestStatus status) {
    return status == DevelopmentRequestStatus.CANCELLED
        || status == DevelopmentRequestStatus.FAILED;
  }

  private static <T> T fail(String message) {
    throw new IllegalArgumentException(message);
  }
}
