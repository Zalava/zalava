package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemBinaryArtifactInstallation;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInspection;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemLocalArtifactInstallRequestStore;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.catalog.install.application.DefaultBinaryModuleInstallation;
import org.zalava.catalog.install.application.DefaultLocalArtifactModuleInstallation;
import org.zalava.development.adapter.in.agent.DevelopmentRequestTools;
import org.zalava.development.adapter.out.filesystem.FileSystemDevelopmentWorkspaceExporter;
import org.zalava.development.application.DefaultDevelopmentCandidateSubmission;
import org.zalava.development.application.DefaultDevelopmentRequestManagement;
import org.zalava.development.application.DefaultDevelopmentWorkspaceExport;
import org.zalava.development.application.DevelopmentCandidateEvaluator;
import org.zalava.development.application.DevelopmentCandidateValidationGateway;
import org.zalava.development.application.port.out.DevelopmentRequestStore;
import org.zalava.runtime.ExternalSeaModuleLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DevelopmentCandidateSubmissionTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-25T14:00:00Z"), ZoneOffset.UTC);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String EXPOSED_INPUT_SCHEMA = "{\"type\":\"object\"}";
  @TempDir Path trustedRoot;
  @TempDir Path managedWorkspace;

  @Test
  void provesAgentFacingExportAndSubmissionRejectsToolFailureThenAcceptsAnExternalFixture()
      throws Exception {
    InMemoryStore store = new InMemoryStore();
    var requests = new DefaultDevelopmentRequestManagement(store, CLOCK);
    var candidates = submission(store);
    var tools =
        new DevelopmentRequestTools(
            requests,
            new DefaultDevelopmentWorkspaceExport(
                store, new FileSystemDevelopmentWorkspaceExporter()),
            candidates);
    JsonNode created =
        JSON.readTree(tools.create(contract("{\"fail\":true}"), "Fixture manual Codex pilot"));
    DevelopmentRequestId requestId =
        new DevelopmentRequestId(created.path("requestId").stringValue(""));
    Path workspace = managedWorkspace.resolve("exported-workspace").toAbsolutePath();

    JsonNode exported = JSON.readTree(tools.export(requestId.value(), workspace.toString()));
    JsonNode begun = JSON.readTree(tools.begin(requestId.value()));
    assertThat(exported.path("workspacePath").stringValue("")).isEqualTo(workspace.toString());
    assertThat(workspace.resolve(".sea-request/development-contract.yaml")).exists();
    assertThat(begun.path("status").stringValue("")).isEqualTo("IN_DEVELOPMENT");

    Path rejectedArtifact =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("rejected.jar"));
    JSON.readTree(tools.submit(requestId.value(), rejectedArtifact.toString()));
    ModuleDevelopmentRequest rejected = store.get(requestId);
    assertThat(rejected.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(rejected.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.evaluation().markdownReport())
                    .contains("Requested tool did not return success"));

    JSON.readTree(
        tools.revise(requestId.value(), contract("{}"), "Correct rejected fixture candidate"));
    Path acceptedArtifact =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("accepted.jar"));
    JsonNode submitted =
        JSON.readTree(tools.submit(requestId.value(), acceptedArtifact.toString()));
    ModuleDevelopmentRequest accepted = store.get(requestId);
    assertThat(accepted.status()).isEqualTo(DevelopmentRequestStatus.READY_TO_INSTALL);
    assertThat(accepted.candidateAttempts()).hasSize(2);
    assertThat(submitted.path("candidateAttempts").get(1).path("accepted").asBoolean()).isTrue();

    FileSystemModuleEnablement enablement = new FileSystemModuleEnablement(managedWorkspace);
    var installation =
        new DefaultLocalArtifactModuleInstallation(
            new FileSystemLocalArtifactInstallRequestStore(managedWorkspace),
            new DefaultBinaryModuleInstallation(
                new FileSystemBinaryArtifactInstallation(managedWorkspace), enablement),
            new FileSystemLocalArtifactInspection(List.of(trustedRoot)),
            gateway(store),
            CLOCK);
    var approval = installation.create(fixtureModule(), acceptedArtifact.toString(), requestId);
    assertThat(approval.status())
        .isEqualTo(org.zalava.catalog.LocalArtifactInstallRequest.Status.PENDING);
    assertThat(installation.allow(approval.requestId()).status())
        .isEqualTo(org.zalava.catalog.LocalArtifactInstallRequest.Status.SUCCEEDED);
    assertThat(requests.transition(requestId, DevelopmentRequestStatus.INSTALLED).status())
        .isEqualTo(DevelopmentRequestStatus.INSTALLED);

    try (ExternalSeaModuleLoader restarted =
        new ExternalSeaModuleLoader(new FileSystemModuleEnablement(managedWorkspace))) {
      var provider =
          restarted
              .loadModules()
              .getFirst()
              .providerFactories()
              .getFirst()
              .createProviders(org.zalava.ProviderFactoryContext.empty())
              .getFirst();
      assertThat(provider.listTools())
          .extracting(org.zalava.ZalavaToolDescriptor::name)
          .contains("example_lookup");
      assertThat(
              provider
                  .callTool(
                      "example_lookup",
                      new tools.jackson.databind.ObjectMapper().createObjectNode(),
                      org.zalava.InvocationContext.system())
                  .success())
          .isTrue();
    }
  }

  @Test
  void acceptsTrustedFixtureAndRetainsDeterministicEvidenceWithoutInstalling() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request = inDevelopment(store);
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("fixture.jar"));
    var submission = submission(store);

    ModuleDevelopmentRequest evaluated = submission.submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.READY_TO_INSTALL);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt -> {
              assertThat(attempt.sha256Digest()).startsWith("sha256:");
              assertThat(attempt.evaluation().accepted()).isTrue();
              assertThat(attempt.evaluation().jsonReport())
                  .contains("ACCEPTED", request.id().value());
              assertThat(attempt.evaluation().markdownReport())
                  .contains("Module candidate evaluation", "example_lookup");
            });
  }

  @Test
  void acceptsDynamicResponseWhenItSatisfiesTheAcceptanceScenario() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request =
        inDevelopment(store, contract("{}", "{\"value\":\"different\"}"));
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("mismatched-example.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.READY_TO_INSTALL);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.evaluation().markdownReport())
                    .contains("satisfying the acceptance scenario"));
  }

  @Test
  void verifiesExpectedErrorEnvelopesAndMarksUnexercisedExternalRequirementsDeclared()
      throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentContract base = contract();
    ModuleDevelopmentContract requestContract =
        new ModuleDevelopmentContract(
            base.module(),
            base.purpose(),
            base.targetSeaApiVersion(),
            base.tools(),
            List.of(new ModuleDevelopmentContract.ExpectedError("UNKNOWN_TOOL", "Unknown tool")),
            List.of(
                new ModuleDevelopmentContract.AcceptanceScenario(
                    "happy",
                    "{}",
                    List.of(
                        new ModuleDevelopmentContract.ResponseAssertion(
                            "$.value", "equals", "\"fixture\"")),
                    null),
                new ModuleDevelopmentContract.AcceptanceScenario(
                    "failure", "{\"fail\":true}", List.of(), "FIXTURE_FAILURE")),
            new ModuleDevelopmentContract.OperationalRequirements(
                1_000L, 10_000L, true, List.of("weather-api"), false, false, "25"),
            base.deliveryRequirements());
    ModuleDevelopmentRequest request = inDevelopment(store, requestContract);
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("error-envelope.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.READY_TO_INSTALL);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt -> {
              assertThat(attempt.evaluation().decision())
                  .isEqualTo(CandidateEvaluation.Decision.ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS);
              assertThat(attempt.evaluation().requirements())
                  .anySatisfy(
                      requirement -> {
                        assertThat(requirement.id()).isEqualTo("scenario.failure");
                        assertThat(requirement.status())
                            .isEqualTo(CandidateEvaluation.RequirementStatus.VERIFIED);
                      })
                  .anySatisfy(
                      requirement -> {
                        assertThat(requirement.id()).isEqualTo("operational.network-access");
                        assertThat(requirement.status())
                            .isEqualTo(CandidateEvaluation.RequirementStatus.DECLARED);
                      });
              assertThat(attempt.evaluation().jsonReport())
                  .contains("ACCEPTED_WITH_UNVERIFIED_REQUIREMENTS", "FIXTURE_FAILURE", "DECLARED");
            });
  }

  @Test
  void rejectsResponsesThatExceedTheAuthoritativeResponseLimit() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentContract base = contract();
    ModuleDevelopmentContract requestContract =
        new ModuleDevelopmentContract(
            base.module(),
            base.purpose(),
            base.targetSeaApiVersion(),
            base.tools(),
            base.expectedErrors(),
            base.acceptanceScenarios(),
            new ModuleDevelopmentContract.OperationalRequirements(
                1_000L, 1L, false, List.of(), false, false, "25"),
            base.deliveryRequirements());
    ModuleDevelopmentRequest request = inDevelopment(store, requestContract);
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("response-limit.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.evaluation().requirements())
                    .anySatisfy(
                        requirement ->
                            assertThat(requirement.status())
                                .isEqualTo(CandidateEvaluation.RequirementStatus.FAILED)));
  }

  @Test
  void rejectsARequestedInputSchemaThatDoesNotStrictlyMatchTheExposedToolSchema() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request =
        inDevelopment(
            store,
            contractWithSchemas(
                "{\"type\":\"object\",\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}}}",
                "{\"type\":\"object\"}"));
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("schema-mismatch.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.evaluation().markdownReport())
                    .contains("Requested and exposed input schemas differ"));
  }

  @Test
  void rejectsASuccessfulResponseThatViolatesTheAuthoritativeOutputSchemaAtItsPath()
      throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request =
        inDevelopment(
            store,
            contractWithSchemas(
                EXPOSED_INPUT_SCHEMA,
                "{\"type\":\"object\",\"required\":[\"result\"],\"properties\":{\"result\":{\"type\":\"integer\"}}}"));
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("output-mismatch.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.evaluation().markdownReport())
                    .contains("Successful response violates schema at $"));
  }

  @Test
  void recordsSanitizedToolInputAndReturnedFailureEnvelopeForRejectedCandidate() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request =
        inDevelopment(store, contract("{\"fail\":true,\"apiToken\":\"private-token\"}"));
    Path fixture =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            trustedRoot.resolve("failed-invocation.jar"));

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), fixture.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .satisfies(
            attempt -> {
              assertThat(attempt.evaluation().invocations())
                  .singleElement()
                  .satisfies(
                      invocation -> {
                        assertThat(invocation.toolName()).isEqualTo("example_lookup");
                        assertThat(invocation.inputJson())
                            .contains("\"fail\":true", "\"apiToken\":\"[REDACTED]\"")
                            .doesNotContain("private-token");
                        assertThat(invocation.success()).isFalse();
                        assertThat(invocation.responseJson()).contains("FIXTURE_FAILURE");
                        assertThat(invocation.elapsedMillis()).isGreaterThanOrEqualTo(0L);
                      });
              assertThat(attempt.evaluation().jsonReport()).contains("invocations", "[REDACTED]");
            });
  }

  @Test
  void rejectsInvalidTrustedJarAndKeepsItOutOfInstallation() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request = inDevelopment(store);
    Path invalid = Files.writeString(trustedRoot.resolve("invalid.jar"), "not a jar");

    ModuleDevelopmentRequest evaluated = submission(store).submit(request.id(), invalid.toString());

    assertThat(evaluated.status()).isEqualTo(DevelopmentRequestStatus.REVISION_REQUIRED);
    assertThat(evaluated.candidateAttempts())
        .singleElement()
        .extracting(attempt -> attempt.evaluation().jsonReport())
        .asString()
        .contains("REJECTED");
  }

  @Test
  void refusesCandidatesOutsideConfiguredTrustedRoot() throws Exception {
    InMemoryStore store = new InMemoryStore();
    ModuleDevelopmentRequest request = inDevelopment(store);
    Path outside = Files.createTempFile("outside-candidate", ".jar");

    assertThatThrownBy(() -> submission(store).submit(request.id(), outside.toString()))
        .hasMessageContaining("trusted root");
    assertThat(store.get(request.id()).candidateAttempts()).isEmpty();
  }

  private ModuleDevelopmentRequest inDevelopment(InMemoryStore store) {
    return inDevelopment(store, contract());
  }

  private ModuleDevelopmentRequest inDevelopment(
      InMemoryStore store, ModuleDevelopmentContract contract) {
    var requests = new DefaultDevelopmentRequestManagement(store, CLOCK);
    ModuleDevelopmentRequest request = requests.create(contract, "Fixture request");
    requests.transition(request.id(), DevelopmentRequestStatus.EXPORTED);
    return requests.transition(request.id(), DevelopmentRequestStatus.IN_DEVELOPMENT);
  }

  private DefaultDevelopmentCandidateSubmission submission(InMemoryStore store) {
    return new DefaultDevelopmentCandidateSubmission(
        new FileSystemLocalArtifactInspection(List.of(trustedRoot)), gateway(store));
  }

  private DevelopmentCandidateValidationGateway gateway(InMemoryStore store) {
    return new DevelopmentCandidateValidationGateway(
        store, new DevelopmentCandidateEvaluator(CLOCK), CLOCK);
  }

  private static ModuleDevelopmentContract contract() {
    return contract("{}");
  }

  private static ModuleDevelopmentContract contract(String exampleInput) {
    return contract(exampleInput, "{\"value\":\"fixture\"}");
  }

  private static ModuleDevelopmentContract contract(String exampleInput, String expectedOutput) {
    return contractWithSchemas(
        EXPOSED_INPUT_SCHEMA, "{\"type\":\"object\"}", exampleInput, expectedOutput);
  }

  private static ModuleDevelopmentContract contractWithSchemas(
      String inputSchema, String outputSchema) {
    return contractWithSchemas(inputSchema, outputSchema, "{}", "{\"value\":\"fixture\"}");
  }

  private static ModuleDevelopmentContract contractWithSchemas(
      String inputSchema, String outputSchema, String exampleInput, String expectedOutput) {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("sea-external-module-fixture", "1.0.0"),
        "Fixture evaluation",
        "1.0.0",
        List.of(
            new ModuleDevelopmentContract.Tool(
                "example_lookup",
                "Fixture lookup",
                inputSchema,
                outputSchema,
                List.of("UNKNOWN_TOOL"),
                List.of(new ModuleDevelopmentContract.ExampleCall(exampleInput, expectedOutput)))),
        List.of(new ModuleDevelopmentContract.ExpectedError("UNKNOWN_TOOL", "Unknown tool")),
        List.of(
            new ModuleDevelopmentContract.AcceptanceScenario(
                "happy",
                "{}",
                List.of(
                    new ModuleDevelopmentContract.ResponseAssertion("$.value", "exists", "true")),
                null)),
        new ModuleDevelopmentContract.OperationalRequirements(
            1_000L, 10_000L, false, List.of(), false, false, "25"),
        new ModuleDevelopmentContract.DeliveryRequirements(
            "jar", "fixture.jar", "1", false, Map.of()));
  }

  private static SourceModuleIndex.Module fixtureModule() {
    return new SourceModuleIndex.Module(
        "sea-external-module-fixture",
        "1.0.0",
        "External fixture",
        "Fixture manual pilot module",
        URI.create("https://example.test/modules/sea-external-module-fixture"),
        new SourceModuleIndex.Artifact("org.zalava", "sea-external-module-fixture", "1.0.0"),
        new SourceModuleIndex.Source(
            URI.create("https://example.test/sea-external-module-fixture"), "Apache-2.0"),
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
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
