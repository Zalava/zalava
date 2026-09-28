package org.zalava.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.zalava.development.adapter.in.agent.DevelopmentRequestTools;
import org.zalava.development.application.DefaultDevelopmentRequestManagement;
import org.zalava.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.development.application.port.in.DevelopmentWorkspaceExport;
import org.zalava.development.application.port.out.DevelopmentRequestStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DevelopmentRequestToolsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void createsAndInspectsBoundedAuthoritativeRequestWithoutCandidateSideEffects() throws Exception {
    Map<DevelopmentRequestId, ModuleDevelopmentRequest> stored = new HashMap<>();
    DevelopmentRequestStore store =
        new DevelopmentRequestStore() {
          @Override
          public ModuleDevelopmentRequest get(DevelopmentRequestId id) {
            return stored.get(id);
          }

          @Override
          public ModuleDevelopmentRequest save(ModuleDevelopmentRequest request) {
            stored.put(request.id(), request);
            return request;
          }
        };
    var management = new DefaultDevelopmentRequestManagement(store, Clock.systemUTC());
    DevelopmentWorkspaceExport exports = (id, root) -> new DevelopmentWorkspace(id, root, Map.of());
    DevelopmentCandidateSubmission candidates =
        (id, path) -> {
          throw new AssertionError("candidate submission must not run");
        };
    var tools = new DevelopmentRequestTools(management, exports, candidates);

    JsonNode created = JSON.readTree(tools.create(contract(), "Add time capability"));
    JsonNode inspected = JSON.readTree(tools.inspect(created.path("requestId").asText()));
    JsonNode exported =
        JSON.readTree(
            tools.export(created.path("requestId").asText(), "/external/sea-module-time"));

    assertThat(created.path("status").asText()).isEqualTo("PREPARED");
    assertThat(inspected.path("moduleId").asText()).isEqualTo("sea-module-time");
    assertThat(inspected.path("installationApproval").asText()).isEqualTo("not_available");
    assertThat(exported.path("workspacePath").asText()).isEqualTo("/external/sea-module-time");
    assertThat(exported.path("codexCommand").asText())
        .isEqualTo("cd /external/sea-module-time && codex");
    assertThat(exported.path("initialPrompt").asText())
        .isEqualTo("Read .sea-request/CODEX_TASK.md and begin the implementation.");
    assertThat(exported.path("nextSteps")).hasSize(3);

    management.transition(
        new DevelopmentRequestId(created.path("requestId").asText()),
        DevelopmentRequestStatus.EXPORTED);
    JsonNode started = JSON.readTree(tools.begin(created.path("requestId").asText()));

    assertThat(started.path("status").asText()).isEqualTo("IN_DEVELOPMENT");
    assertThat(started.path("candidateAttempts")).isEmpty();
  }

  private static ModuleDevelopmentContract contract() {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("sea-module-time", "1.0.0"),
        "Return time",
        "1.0.0",
        java.util.List.of(
            new ModuleDevelopmentContract.Tool(
                "time",
                "Returns time",
                "{}",
                "{}",
                java.util.List.of(),
                java.util.List.of(new ModuleDevelopmentContract.ExampleCall("{}", "{}")))),
        java.util.List.of(),
        java.util.List.of(
            new ModuleDevelopmentContract.AcceptanceScenario(
                "time",
                "{}",
                java.util.List.of(
                    new ModuleDevelopmentContract.ResponseAssertion("$.time", "exists", "true")),
                null)),
        new ModuleDevelopmentContract.OperationalRequirements(
            null, null, null, java.util.List.of(), null, null, null),
        new ModuleDevelopmentContract.DeliveryRequirements(
            "jar", "sea-module-time-*.jar", "1", false, Map.of()));
  }
}
