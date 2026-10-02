package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.zalava.modules.development.adapter.in.agent.DevelopmentRequestTools;
import org.zalava.support.ProviderEnabledComponentTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ProviderEnabledComponentTest
@TestPropertySource(properties = "agent.tools.playwright.enabled=false")
class DevelopmentCandidateWorkflowComponentTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private DevelopmentRequestTools tools;
  @Autowired private Environment environment;

  @Test
  void drivesTheAgentFacingCandidateWorkflowAndExposesPersistedInvocationEvidence()
      throws Exception {
    JsonNode created = JSON.readTree(tools.create(contract(), "Verify external fixture workflow"));
    String requestId = created.path("requestId").stringValue("");
    Path workspace = workspace();
    Path exportedWorkspace = workspace.resolve("external-module-workspace").toAbsolutePath();
    Path artifact =
        Files.copy(
            Path.of(System.getProperty("sea.test.external-module-jar")),
            workspace.resolve("fixture.jar"));

    JsonNode exported = JSON.readTree(tools.export(requestId, exportedWorkspace.toString()));
    JsonNode begun = JSON.readTree(tools.begin(requestId));
    JsonNode submitted = JSON.readTree(tools.submit(requestId, artifact.toString()));
    JsonNode inspected = JSON.readTree(tools.inspect(requestId));

    assertThat(exported.path("workspacePath").stringValue(""))
        .isEqualTo(exportedWorkspace.toString());
    assertThat(exportedWorkspace.resolve(".sea-request/development-contract.yaml")).exists();
    assertThat(begun.path("status").stringValue("")).isEqualTo("IN_DEVELOPMENT");
    assertThat(submitted.path("status").stringValue("")).isEqualTo("READY_TO_INSTALL");
    assertThat(inspected.path("candidateAttempts")).hasSize(1);
    JsonNode invocation = inspected.path("candidateAttempts").get(0).path("invocations").get(0);
    assertThat(invocation.path("toolName").stringValue("")).isEqualTo("example_lookup");
    assertThat(invocation.path("inputJson").stringValue("")).isEqualTo("{}");
    assertThat(invocation.path("responseJson").stringValue(""))
        .isEqualTo("{\"value\":\"fixture\"}");
    assertThat(invocation.path("success").asBoolean()).isTrue();
    assertThat(invocation.path("elapsedMillis").asLong()).isGreaterThanOrEqualTo(0L);
  }

  private static ModuleDevelopmentContract contract() {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("sea-external-module-fixture", "1.0.0"),
        "Fixture evaluation",
        "1.0.0",
        List.of(
            new ModuleDevelopmentContract.Tool(
                "example_lookup",
                "Fixture lookup",
                "{\"type\":\"object\"}",
                "{\"type\":\"object\"}",
                List.of("UNKNOWN_TOOL"),
                List.of(
                    new ModuleDevelopmentContract.ExampleCall("{}", "{\"value\":\"fixture\"}")))),
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

  private Path workspace() {
    return Path.of(java.net.URI.create(environment.getRequiredProperty("agent.workspace")));
  }
}
