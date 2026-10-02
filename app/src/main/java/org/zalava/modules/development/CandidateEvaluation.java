package org.zalava.modules.development;

import java.time.Instant;
import java.util.List;

public record CandidateEvaluation(
    Decision decision,
    Instant evaluatedAt,
    List<String> evidence,
    String jsonReport,
    String markdownReport,
    List<Invocation> invocations,
    List<Requirement> requirements) {
  public CandidateEvaluation {
    decision = java.util.Objects.requireNonNull(decision, "decision must not be null");
    evaluatedAt = java.util.Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    evidence =
        evidence == null || evidence.isEmpty()
            ? List.of("No evaluation evidence")
            : List.copyOf(evidence);
    if (jsonReport == null || jsonReport.isBlank())
      throw new IllegalArgumentException("jsonReport must not be blank");
    if (markdownReport == null || markdownReport.isBlank())
      throw new IllegalArgumentException("markdownReport must not be blank");
    invocations = invocations == null ? List.of() : List.copyOf(invocations);
    requirements = requirements == null ? List.of() : List.copyOf(requirements);
  }

  /**
   * Compatibility projection for callers that only need to know whether installation may be
   * requested.
   */
  public boolean accepted() {
    return decision != Decision.REJECTED;
  }

  public CandidateEvaluation(
      boolean accepted,
      Instant evaluatedAt,
      List<String> evidence,
      String jsonReport,
      String markdownReport,
      List<Invocation> invocations) {
    this(
        accepted ? Decision.ACCEPTED : Decision.REJECTED,
        evaluatedAt,
        evidence,
        jsonReport,
        markdownReport,
        invocations,
        List.of());
  }

  public enum Decision {
    ACCEPTED,
    ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS,
    REJECTED
  }

  public enum RequirementStatus {
    VERIFIED,
    DECLARED,
    FAILED,
    NOT_TESTABLE
  }

  public record Requirement(String id, RequirementStatus status, String expected, String actual) {
    public Requirement {
      if (id == null || id.isBlank())
        throw new IllegalArgumentException("requirement id must not be blank");
      status = java.util.Objects.requireNonNull(status, "requirement status must not be null");
      expected = expected == null ? "" : expected;
      actual = actual == null ? "" : actual;
    }
  }

  public record Invocation(
      String toolName, String inputJson, String responseJson, Boolean success, long elapsedMillis) {
    public Invocation {
      if (toolName == null || toolName.isBlank())
        throw new IllegalArgumentException("toolName must not be blank");
      if (inputJson == null || inputJson.isBlank())
        throw new IllegalArgumentException("inputJson must not be blank");
      if (responseJson == null || responseJson.isBlank())
        throw new IllegalArgumentException("responseJson must not be blank");
      if (elapsedMillis < 0)
        throw new IllegalArgumentException("elapsedMillis must not be negative");
    }
  }
}
