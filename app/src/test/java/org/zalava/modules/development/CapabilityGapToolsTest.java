package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.zalava.modules.development.adapter.in.agent.CapabilityGapTools;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CapabilityGapToolsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void returnsBoundedRecommendationWithoutCreatingADevelopmentRequest() throws Exception {
    JsonNode result =
        JSON.readTree(
            new CapabilityGapTools()
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
