package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.development.application.DefaultDevelopmentRequestManagement;
import org.zalava.development.application.port.out.DevelopmentRequestStore;

class ModuleDevelopmentRequestTest {

  private final DefaultDevelopmentRequestManagement requests =
      new DefaultDevelopmentRequestManagement(
          new InMemoryStore(), Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void revisionReplacesTheAuthoritativeContractWithoutMutatingEarlierRevisions() {
    ModuleDevelopmentRequest created = requests.create(contract("0.1.0"), "Initial request");

    ModuleDevelopmentRequest revised =
        requests.revise(created.id(), contract("0.2.0"), "Add the requested response field");

    assertThat(revised.status()).isEqualTo(DevelopmentRequestStatus.PREPARED);
    assertThat(revised.revisions()).hasSize(2);
    assertThat(revised.revisions().getFirst().contract().module().versionPolicy())
        .isEqualTo("0.1.0");
    assertThat(revised.currentRevision().contract().module().versionPolicy()).isEqualTo("0.2.0");
    assertThat(created.revisions()).hasSize(1);
  }

  @Test
  void transitionsThroughThePilotLifecycleAndRejectsInvalidTransitions() {
    ModuleDevelopmentRequest created = requests.create(contract("0.1.0"), "Initial request");
    assertThatThrownBy(
            () -> requests.transition(created.id(), DevelopmentRequestStatus.READY_TO_INSTALL))
        .isInstanceOf(DevelopmentRequestException.class)
        .hasMessage("Invalid lifecycle transition: PREPARED -> READY_TO_INSTALL");

    ModuleDevelopmentRequest ready =
        requests.transition(created.id(), DevelopmentRequestStatus.EXPORTED);
    ready = requests.transition(ready.id(), DevelopmentRequestStatus.IN_DEVELOPMENT);
    ready = requests.transition(ready.id(), DevelopmentRequestStatus.CANDIDATE_SUBMITTED);
    ready = requests.transition(ready.id(), DevelopmentRequestStatus.VALIDATING);
    ready = requests.transition(ready.id(), DevelopmentRequestStatus.READY_TO_INSTALL);
    ready = requests.transition(ready.id(), DevelopmentRequestStatus.INSTALLED);

    assertThat(ready.status()).isEqualTo(DevelopmentRequestStatus.INSTALLED);
  }

  @Test
  void revisionRequiredReturnsToDevelopmentWhenRevised() {
    ModuleDevelopmentRequest created = requests.create(contract("0.1.0"), "Initial request");
    requests.transition(created.id(), DevelopmentRequestStatus.EXPORTED);
    requests.transition(created.id(), DevelopmentRequestStatus.IN_DEVELOPMENT);
    requests.transition(created.id(), DevelopmentRequestStatus.CANDIDATE_SUBMITTED);
    requests.transition(created.id(), DevelopmentRequestStatus.VALIDATING);
    requests.transition(created.id(), DevelopmentRequestStatus.REVISION_REQUIRED);

    ModuleDevelopmentRequest revised =
        requests.revise(created.id(), contract("0.1.1"), "Correct response contract");

    assertThat(revised.status()).isEqualTo(DevelopmentRequestStatus.IN_DEVELOPMENT);
    assertThat(revised.currentRevision().number()).isEqualTo(2);
  }

  @Test
  void rejectsAVagueModuleVersionBeforeExportOrCandidateSubmission() {
    assertThatThrownBy(() -> requests.create(contract("semver"), "Invalid version"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("versionPolicy must be a concrete semantic version for the pilot");

    ModuleDevelopmentRequest created = requests.create(contract("1.0.0"), "Initial request");
    assertThatThrownBy(() -> requests.revise(created.id(), contract("1.0"), "Invalid version"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("versionPolicy must be a concrete semantic version for the pilot");
  }

  @Test
  void seaOwnedApiVersionReplacesTheCallerSuppliedContractValue() {
    DefaultDevelopmentRequestManagement seaOwnedRequests =
        new DefaultDevelopmentRequestManagement(
            new InMemoryStore(),
            Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC),
            "1.0.1");

    ModuleDevelopmentRequest created =
        seaOwnedRequests.create(contract("1.0.0"), "Initial request");

    assertThat(created.currentRevision().contract().targetSeaApiVersion()).isEqualTo("1.0.1");
  }

  @Test
  void rejectsANonConcreteSeaOwnedApiVersion() {
    assertThatThrownBy(
            () ->
                new DefaultDevelopmentRequestManagement(
                    new InMemoryStore(), Clock.systemUTC(), "1.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("module API version must be a concrete semantic version");
  }

  static ModuleDevelopmentContract contract(String version) {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("zalava-module-example", version),
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
      return values.get(id);
    }

    @Override
    public ModuleDevelopmentRequest save(ModuleDevelopmentRequest request) {
      values.put(request.id(), request);
      return request;
    }
  }
}
