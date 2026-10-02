package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateEvaluationTest {

  @Test
  void suppliesDefaultEvidenceAndDefensivelyCopiesEvidence() {
    CandidateEvaluation emptyEvidence =
        new CandidateEvaluation(
            CandidateEvaluation.Decision.ACCEPTED,
            Instant.parse("2026-09-01T00:00:00Z"),
            List.of(),
            "{}",
            "# report",
            null,
            null);

    assertThat(emptyEvidence.evidence()).containsExactly("No evaluation evidence");
    assertThat(emptyEvidence.invocations()).isEmpty();
    assertThat(emptyEvidence.requirements()).isEmpty();
  }

  @Test
  void preservesRejectedEvaluationWithVerifiedRequirements() {
    CandidateEvaluation.Requirement verified =
        new CandidateEvaluation.Requirement(
            "descriptor", CandidateEvaluation.RequirementStatus.VERIFIED, "valid", "valid");

    CandidateEvaluation evaluation =
        new CandidateEvaluation(
            CandidateEvaluation.Decision.REJECTED,
            Instant.parse("2026-09-01T00:00:00Z"),
            List.of("checked"),
            "{}",
            "# report",
            List.of(),
            List.of(verified));

    assertThat(evaluation.decision()).isEqualTo(CandidateEvaluation.Decision.REJECTED);
  }

  @Test
  void validatesRequirementAndInvocationBoundaries() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CandidateEvaluation.Requirement(
                    " ", CandidateEvaluation.RequirementStatus.VERIFIED, null, null))
        .withMessage("requirement id must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CandidateEvaluation.Invocation(" ", "{}", "{}", true, 0))
        .withMessage("toolName must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CandidateEvaluation.Invocation("tool", " ", "{}", true, 0))
        .withMessage("inputJson must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CandidateEvaluation.Invocation("tool", "{}", " ", true, 0))
        .withMessage("responseJson must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CandidateEvaluation.Invocation("tool", "{}", "{}", true, -1))
        .withMessage("elapsedMillis must not be negative");
  }
}
