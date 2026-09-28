package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the lifecycle branches of the development request aggregate that the application-service
 * tests do not reach: constructor validation, the legacy four-argument constructor, revision
 * gating, candidate bookkeeping, and the full allowed-transition matrix including terminal states.
 */
class ModuleDevelopmentRequestLifecycleTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void constructsThroughTheLegacyFourArgumentConstructor() {
    var request =
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId("req-1"),
            NOW,
            DevelopmentRequestStatus.PREPARED,
            List.of(revision(1)));

    assertThat(request.candidateAttempts()).isEmpty();
    assertThat(request.currentRevision()).isEqualTo(revision(1));
  }

  @Test
  void rejectsNullIdentityTimestampsAndStatus() {
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest(
                    null, NOW, DevelopmentRequestStatus.PREPARED, List.of(revision(1))))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("id must not be null");
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest(
                    new DevelopmentRequestId("req-1"),
                    null,
                    DevelopmentRequestStatus.PREPARED,
                    List.of(revision(1))))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("createdAt must not be null");
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest(
                    new DevelopmentRequestId("req-1"), NOW, null, List.of(revision(1))))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("status must not be null");
  }

  @Test
  void rejectsEmptyRevisionListsAndNonContiguousNumbers() {
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest(
                    new DevelopmentRequestId("req-1"),
                    NOW,
                    DevelopmentRequestStatus.PREPARED,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("revisions must not be empty");
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest(
                    new DevelopmentRequestId("req-1"),
                    NOW,
                    DevelopmentRequestStatus.PREPARED,
                    List.of(revision(2))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("revision numbers must be contiguous from one");
  }

  @Test
  void revisionValidationRejectsEveryInvalidRevisionField() {
    assertThatThrownBy(() -> revision(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("revision number must be positive");
    assertThatThrownBy(() -> new ModuleDevelopmentRequest.Revision(1, null, "reason", contract()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("revisedAt must not be null");
    assertThatThrownBy(() -> new ModuleDevelopmentRequest.Revision(1, NOW, " ", contract()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("revision reason must not be blank");
    assertThatThrownBy(() -> new ModuleDevelopmentRequest.Revision(1, NOW, "reason", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("contract must not be null");
  }

  @Test
  void reviseIsOnlyAllowedBeforeTheRequestIsCommittedToValidation() {
    var request = request(DevelopmentRequestStatus.REVISION_REQUIRED);

    ModuleDevelopmentRequest revised =
        request.revise(contract("2.0.0"), "Second attempt", NOW.plusSeconds(60));

    assertThat(revised.status()).isEqualTo(DevelopmentRequestStatus.IN_DEVELOPMENT);
    assertThat(revised.currentRevision().number()).isEqualTo(2);
    assertThat(revised.currentRevision().reason()).isEqualTo("Second attempt");
  }

  @Test
  void reviseKeepsTheStatusForNonRevisionRequiredStates() {
    var request = request(DevelopmentRequestStatus.EXPORTED);

    ModuleDevelopmentRequest revised =
        request.revise(contract("2.0.0"), "Tune the contract", NOW.plusSeconds(60));

    assertThat(revised.status()).isEqualTo(DevelopmentRequestStatus.EXPORTED);
  }

  @Test
  void reviseIsRefusedOnceTheCandidateIsSubmitted() {
    var request = request(DevelopmentRequestStatus.CANDIDATE_SUBMITTED);

    assertThatThrownBy(() -> request.revise(contract("2.0.0"), "Too late", NOW))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("Request cannot be revised while CANDIDATE_SUBMITTED");
  }

  @Test
  void transitionsToTheSameStatusAreNoOps() {
    var request = request(DevelopmentRequestStatus.VALIDATING);

    assertThat(request.transitionTo(DevelopmentRequestStatus.VALIDATING)).isSameAs(request);
  }

  @Test
  void anyNonTerminalStateMayTransitionToTheFailedTerminalState() {
    for (DevelopmentRequestStatus from : DevelopmentRequestStatus.values()) {
      if (from == DevelopmentRequestStatus.INSTALLED
          || from == DevelopmentRequestStatus.CANCELLED
          || from == DevelopmentRequestStatus.FAILED) {
        continue;
      }
      var request = request(from);
      assertThat(request.transitionTo(DevelopmentRequestStatus.FAILED).status())
          .as("fail from %s", from)
          .isEqualTo(DevelopmentRequestStatus.FAILED);
    }
  }

  @Test
  void terminalStatesRejectEveryTransition() {
    for (DevelopmentRequestStatus terminal :
        new DevelopmentRequestStatus[] {
          DevelopmentRequestStatus.INSTALLED,
          DevelopmentRequestStatus.CANCELLED,
          DevelopmentRequestStatus.FAILED
        }) {
      var request = request(terminal);
      for (DevelopmentRequestStatus target : DevelopmentRequestStatus.values()) {
        if (target == terminal) {
          continue;
        }
        assertThatThrownBy(() -> request.transitionTo(target))
            .as("%s -> %s", terminal, target)
            .isInstanceOf(DevelopmentRequestException.class)
            .hasMessage("Invalid lifecycle transition: " + terminal + " -> " + target);
      }
    }
  }

  @Test
  void theFullHappyPathRemainsAllowedIncludingValidationOutcomes() {
    var request = request(DevelopmentRequestStatus.PREPARED);
    request = request.transitionTo(DevelopmentRequestStatus.EXPORTED);
    request = request.transitionTo(DevelopmentRequestStatus.IN_DEVELOPMENT);
    request = request.transitionTo(DevelopmentRequestStatus.CANDIDATE_SUBMITTED);
    request = request.transitionTo(DevelopmentRequestStatus.VALIDATING);
    request = request.transitionTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(request.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);

    var ready = request(DevelopmentRequestStatus.READY_TO_INSTALL);
    assertThat(ready.transitionTo(DevelopmentRequestStatus.INSTALLED).status())
        .isEqualTo(DevelopmentRequestStatus.INSTALLED);
  }

  @Test
  void recordCandidateRequiresTheInDevelopmentStateAndContiguousNumbers() {
    var prepared = request(DevelopmentRequestStatus.PREPARED);
    assertThatThrownBy(() -> prepared.recordCandidate(attempt(1)))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("Candidate can only be submitted while IN_DEVELOPMENT");

    var inDevelopment = request(DevelopmentRequestStatus.IN_DEVELOPMENT);
    assertThatThrownBy(() -> inDevelopment.recordCandidate(attempt(2)))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("candidate attempt numbers must be contiguous from one");
  }

  @Test
  void recordEvaluationRequiresASubmittedCandidateAndChoosesTheOutcomeStatus() {
    var inDevelopment = request(DevelopmentRequestStatus.IN_DEVELOPMENT);
    assertThatThrownBy(
            () -> inDevelopment.recordEvaluation(evaluation(CandidateEvaluation.Decision.ACCEPTED)))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("Candidate can only be evaluated after submission");

    var submitted = request(DevelopmentRequestStatus.CANDIDATE_SUBMITTED);
    assertThatThrownBy(
            () -> submitted.recordEvaluation(evaluation(CandidateEvaluation.Decision.ACCEPTED)))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("Candidate evaluation requires a submitted attempt");

    var recorded = request(DevelopmentRequestStatus.IN_DEVELOPMENT).recordCandidate(attempt(1));
    assertThat(recorded.status()).isEqualTo(DevelopmentRequestStatus.CANDIDATE_SUBMITTED);

    var accepted = recorded.recordEvaluation(evaluation(CandidateEvaluation.Decision.ACCEPTED));
    assertThat(accepted.status()).isEqualTo(DevelopmentRequestStatus.READY_TO_INSTALL);

    var rejected =
        request(DevelopmentRequestStatus.IN_DEVELOPMENT)
            .recordCandidate(attempt(1))
            .recordEvaluation(evaluation(CandidateEvaluation.Decision.REJECTED));
    assertThat(rejected.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
  }

  @Test
  void candidateAttemptValidationRejectsInvalidFields() {
    assertThatThrownBy(() -> attempt(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("candidate attempt number must be positive");
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest.CandidateAttempt(
                    1, null, "artifact.jar", digest(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("submittedAt must not be null");
    assertThatThrownBy(
            () -> new ModuleDevelopmentRequest.CandidateAttempt(1, NOW, " ", digest(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("artifactPath must not be blank");
    assertThatThrownBy(
            () ->
                new ModuleDevelopmentRequest.CandidateAttempt(1, NOW, "a.jar", "sha256:xyz", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sha256Digest must be a SHA-256 digest");
  }

  private static ModuleDevelopmentRequest request(DevelopmentRequestStatus status) {
    return new ModuleDevelopmentRequest(
        new DevelopmentRequestId("req-" + status), NOW, status, List.of(revision(1)));
  }

  private static ModuleDevelopmentRequest.Revision revision(int number) {
    return new ModuleDevelopmentRequest.Revision(number, NOW, "Initial contract", contract());
  }

  private static ModuleDevelopmentRequest.CandidateAttempt attempt(int number) {
    return new ModuleDevelopmentRequest.CandidateAttempt(
        number, NOW, "/tmp/candidate.jar", digest(), null);
  }

  private static String digest() {
    return "sha256:" + "a".repeat(64);
  }

  private static CandidateEvaluation evaluation(CandidateEvaluation.Decision decision) {
    return new CandidateEvaluation(
        decision,
        NOW,
        List.of("evidence"),
        "{\"decision\":\"" + decision + "\"}",
        "# Module candidate evaluation",
        List.of(),
        List.of());
  }

  private static ModuleDevelopmentContract contract() {
    return contract("1.0.0");
  }

  private static ModuleDevelopmentContract contract(String version) {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("sea-module-example", version),
        "Provide an example capability",
        "1.0",
        List.of(
            new ModuleDevelopmentContract.Tool(
                "example_lookup",
                "Look up an example",
                "{\"type\":\"object\"}",
                "{\"type\":\"object\"}",
                List.of("INVALID_INPUT"),
                List.of(new ModuleDevelopmentContract.ExampleCall("{}", "{}")))),
        List.of(
            new ModuleDevelopmentContract.ExpectedError("INVALID_INPUT", "The request is invalid")),
        List.of(
            new ModuleDevelopmentContract.AcceptanceScenario(
                "happy-path",
                "{}",
                List.of(
                    new ModuleDevelopmentContract.ResponseAssertion("$.value", "exists", "true")),
                null)),
        new ModuleDevelopmentContract.OperationalRequirements(
            1_000L, 10_000L, false, List.of(), false, false, "25"),
        new ModuleDevelopmentContract.DeliveryRequirements(
            "sea-module", "example-*.jar", "1", false, Map.of("moduleId", "sea-module-example")));
  }
}
