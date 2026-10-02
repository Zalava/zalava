package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.modules.development.adapter.in.agent.DevelopmentRequestTools;
import org.zalava.modules.development.application.DefaultDevelopmentRequestManagement;
import org.zalava.modules.development.application.port.in.DevelopmentCandidateSubmission;
import org.zalava.modules.development.application.port.in.DevelopmentWorkspaceExport;
import org.zalava.modules.development.application.port.out.DevelopmentRequestStore;
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
    JsonNode inspected = JSON.readTree(tools.inspect(created.path("requestId").stringValue("")));
    JsonNode exported =
        JSON.readTree(
            tools.export(
                created.path("requestId").stringValue(""), "/external/zalava-module-time"));

    assertThat(created.path("status").stringValue("")).isEqualTo("PREPARED");
    assertThat(inspected.path("moduleId").stringValue("")).isEqualTo("zalava-module-time");
    assertThat(inspected.path("installationApproval").stringValue("")).isEqualTo("not_available");
    assertThat(exported.path("workspacePath").stringValue(""))
        .isEqualTo("/external/zalava-module-time");
    assertThat(exported.path("codexCommand").stringValue(""))
        .isEqualTo("cd /external/zalava-module-time && codex");
    assertThat(exported.path("initialPrompt").stringValue(""))
        .isEqualTo("Read .sea-request/CODEX_TASK.md and begin the implementation.");
    assertThat(exported.path("nextSteps")).hasSize(3);

    management.transition(
        new DevelopmentRequestId(created.path("requestId").stringValue("")),
        DevelopmentRequestStatus.EXPORTED);
    JsonNode started = JSON.readTree(tools.begin(created.path("requestId").stringValue("")));

    assertThat(started.path("status").stringValue("")).isEqualTo("IN_DEVELOPMENT");
    assertThat(started.path("candidateAttempts")).isEmpty();
  }

  private static ModuleDevelopmentContract contract() {
    return new ModuleDevelopmentContract(
        new ModuleDevelopmentContract.Module("zalava-module-time", "1.0.0"),
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
            "jar", "zalava-module-time-*.jar", "1", false, Map.of()));
  }
}
