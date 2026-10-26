package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.modules.development.adapter.in.agent.CapabilityGapTools;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CapabilityGapToolsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void returnsBoundedRecommendationWithoutCreatingADevelopmentRequest() throws Exception {
    JsonNode result =
        JSON.readTree(
            new CapabilityGapTools(
                    capability -> ModuleSuggestions.Outcome.of(ModuleSuggestions.Status.NO_MATCH),
                    () -> false)
                .recommend("pollen forecast", "Can you tell me tomorrow's pollen forecast?"));

    assertThat(result.path("status").stringValue("")).isEqualTo("REVIEW_REQUIRED");
    assertThat(result.path("capability").stringValue("")).isEqualTo("pollen forecast");
    assertThat(result.path("userRequest").stringValue("")).contains("pollen forecast");
    assertThat(result.path("recommendedWorkflow").stringValue(""))
        .isEqualTo("manual_module_development");
    assertThat(result.path("confirmationRequired").asBoolean()).isTrue();
    assertThat(result.path("automaticActions")).isEmpty();
    assertThat(result.toString())
        .doesNotContain("requestId", "workspacePath", "artifactPath", "installation");
  }

  @Test
  void unconfiguredCatalogIsNotEvidenceForModuleDevelopment() throws Exception {
    JsonNode result = JSON.readTree(new CapabilityGapTools().recommend("jobs", "Create a job"));
    assertThat(result.path("status").asString()).isEqualTo("CATALOG_DISABLED");
    assertThat(result.toString()).doesNotContain("manual_module_development");
  }

  @Test
  void distinguishesStoppedInstalledAndAbsentModulesForBothRoles() throws Exception {
    for (boolean administrator : List.of(true, false)) {
      for (String state : List.of("INSTALLED_NOT_RUNNING", "RUNNING", "NOT_INSTALLED", "UNKNOWN")) {
        var tools =
            new CapabilityGapTools(
                capability ->
                    new ModuleSuggestions.Outcome(
                        ModuleSuggestions.Status.MATCH,
                        List.of(new ModuleSuggestions.Module("time", "Time", "Clock tools"))),
                () -> administrator,
                moduleId -> state);
        JsonNode result = JSON.readTree(tools.recommend("clock", "What time is it?"));
        assertThat(result.path("modules").get(0).path("installationState").asString())
            .isEqualTo(state);
        assertThat(result.path("nextAction").asString())
            .contains("INSTALLED_NOT_RUNNING", "RUNNING", "NOT_INSTALLED", "UNKNOWN")
            .doesNotContain("Installation and loading have not happened");
      }
    }
  }

  @Test
  void rejectsBlankOrOversizedRecommendationInputs() {
    CapabilityGapTools tools = new CapabilityGapTools();

    assertThatThrownBy(() -> tools.recommend(" ", "request"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("capability must contain between 1 and 2000 characters");
    assertThatThrownBy(() -> tools.recommend("capability", "x".repeat(2_001)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userRequest must contain between 1 and 2000 characters");
  }
}
