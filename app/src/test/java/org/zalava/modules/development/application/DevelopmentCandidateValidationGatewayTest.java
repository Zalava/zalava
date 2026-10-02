package org.zalava.modules.development.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.LocalArtifactInspection;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.DevelopmentRequestId;
import org.zalava.modules.development.DevelopmentRequestStatus;
import org.zalava.modules.development.ModuleDevelopmentContract;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;

/**
 * Covers the acceptance-evidence gateway: submit bookkeeping, identity matching, digest-scoped
 * evidence lookup, and accepted-candidate projection. The evaluator is stubbed so no module jar is
 * loaded.
 */
class DevelopmentCandidateValidationGatewayTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);
  private static final LocalArtifactInspection.InspectedArtifact ARTIFACT =
      new LocalArtifactInspection.InspectedArtifact(
          "/tmp/candidate.jar", "sha256:" + "a".repeat(64));

  private final InMemoryStore store = new InMemoryStore();
  private final DevelopmentCandidateValidationGateway gateway =
      new DevelopmentCandidateValidationGateway(
          store, new DevelopmentCandidateEvaluator(CLOCK), CLOCK);

  @Test
  void submitRecordsTheAttemptAndEvaluatesItAgainstTheAuthoritativeContract() {
    ModuleDevelopmentRequest created = created();

    ModuleDevelopmentRequest evaluated = gateway.submit(created.id(), ARTIFACT);

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts()).hasSize(1);
    assertThat(evaluated.candidateAttempts().getFirst().evaluation().accepted()).isFalse();
  }

  @Test
  void requireAcceptedRejectsUnknownRequestsMismatchedIdentitiesAndUnknownDigests() {
    ModuleDevelopmentRequest created = created();

    assertThatThrownBy(
            () ->
                gateway.requireAccepted(new DevelopmentRequestId("absent"), ARTIFACT, "m", "1.0.0"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Development request is not available for binary validation");

    assertThatThrownBy(
            () -> gateway.requireAccepted(created.id(), ARTIFACT, "other-module", "1.0.0"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Development request does not match binary module identity");

    gateway.submit(created.id(), ARTIFACT);
    var otherDigest =
        new LocalArtifactInspection.InspectedArtifact("/tmp/other.jar", "sha256:" + "b".repeat(64));
    assertThatThrownBy(
            () ->
                gateway.requireAccepted(
                    created.id(), otherDigest, "zalava-module-example", "1.0.0"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage(
            "Binary has no accepted development-candidate evaluation for the supplied request");
  }

  @Test
  void evidenceIsBoundToTheAcceptedCandidateAttempt() {
    ModuleDevelopmentRequest submitted =
        store.save(
            created().recordCandidate(acceptedAttempt(1)).recordEvaluation(acceptedEvaluation()));

    var evidence =
        gateway.requireAccepted(submitted.id(), ARTIFACT, "zalava-module-example", "1.0.0");

    assertThat(evidence.developmentRequestId()).isEqualTo(submitted.id().value());
    assertThat(evidence.candidateAttemptNumber()).isEqualTo(1);
    assertThat(evidence.decision()).isEqualTo(CandidateEvaluation.Decision.ACCEPTED);

    var accepted = gateway.acceptedCandidate(submitted.id(), ARTIFACT);
    assertThat(accepted.contract().module().moduleId()).isEqualTo("zalava-module-example");
    assertThat(accepted.evaluation().accepted()).isTrue();
  }

  @Test
  void theGatewayRefusesToConsiderRejectedEvidence() {
    ModuleDevelopmentRequest rejected =
        store.save(
            created().recordCandidate(acceptedAttempt(1)).recordEvaluation(rejectedEvaluation()));

    assertThatThrownBy(
            () ->
                gateway.requireAccepted(rejected.id(), ARTIFACT, "zalava-module-example", "1.0.0"))
        .isInstanceOf(SourceModuleInstallationException.class);
  }

  private static ModuleDevelopmentRequest.CandidateAttempt acceptedAttempt(int number) {
    return new ModuleDevelopmentRequest.CandidateAttempt(
        number, CLOCK.instant(), ARTIFACT.path(), ARTIFACT.sha256Digest(), acceptedEvaluation());
  }

  private static CandidateEvaluation acceptedEvaluation() {
    return evaluation(CandidateEvaluation.Decision.ACCEPTED);
  }

  private static CandidateEvaluation rejectedEvaluation() {
    return evaluation(CandidateEvaluation.Decision.REJECTED);
  }

  private static CandidateEvaluation evaluation(CandidateEvaluation.Decision decision) {
    return new CandidateEvaluation(
        decision,
        CLOCK.instant(),
        List.of("stub evidence"),
        "{\"decision\":\"" + decision + "\"}",
        "# Module candidate evaluation",
        List.of(),
        List.of());
  }

  private ModuleDevelopmentRequest created() {
    ModuleDevelopmentRequest request =
        new ModuleDevelopmentRequest(
            new DevelopmentRequestId("gateway-request"),
            CLOCK.instant(),
            DevelopmentRequestStatus.IN_DEVELOPMENT,
            List.of(revision()));
    return store.save(request);
  }

  private static ModuleDevelopmentRequest.Revision revision() {
    return new ModuleDevelopmentRequest.Revision(
        1, CLOCK.instant(), "Initial contract", contract());
  }

  private static ModuleDevelopmentContract contract() {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("zalava-module-example", "1.0.0"),
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
            "sea-module",
            "example-*.jar",
            "1",
            false,
            Map.of("moduleId", "zalava-module-example")));
  }

  private static final class InMemoryStore implements DevelopmentRequestStore {
    private final Map<DevelopmentRequestId, ModuleDevelopmentRequest> values = new HashMap<>();

    @Override
    public ModuleDevelopmentRequest get(DevelopmentRequestId id) {
      ModuleDevelopmentRequest request = values.get(id);
      if (request == null) {
        throw new IllegalArgumentException("No development request " + id);
      }
      return request;
    }

    @Override
    public ModuleDevelopmentRequest save(ModuleDevelopmentRequest request) {
      values.put(request.id(), request);
      return request;
    }
  }
}
