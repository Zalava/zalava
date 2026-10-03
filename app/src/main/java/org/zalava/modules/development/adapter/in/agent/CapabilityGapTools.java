package org.zalava.modules.development.adapter.in.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.ObjectMapper;

/** Recommendation-only agent adapter for a deterministic missing Zalava capability. */
public final class CapabilityGapTools {

  private static final int MAX_FIELD_LENGTH = 2_000;
  private static final ObjectMapper JSON = new ObjectMapper();

  @Tool(
      name = "recommendModuleDevelopment",
      description =
          "Records no persistent state. Use only when Zalava provider-tool discovery found no match. It returns a reviewable recommendation and requires explicit user confirmation before createModuleDevelopmentRequest may create the authoritative request.")
  public String recommend(
      @ToolParam(description = "Concise missing capability") String capability,
      @ToolParam(description = "The user's bounded request that had no Zalava provider-tool match")
          String userRequest) {
    Map<String, Object> recommendation = new LinkedHashMap<>();
    recommendation.put("status", "REVIEW_REQUIRED");
    recommendation.put("capability", required(capability, "capability"));
    recommendation.put("userRequest", required(userRequest, "userRequest"));
    recommendation.put("recommendedWorkflow", "manual_module_development");
    recommendation.put(
        "nextAction", "Ask the user whether to create a reviewable module-development request.");
    recommendation.put("confirmationRequired", true);
    recommendation.put("automaticActions", java.util.List.of());
    return json(recommendation);
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank() || value.length() > MAX_FIELD_LENGTH) {
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + MAX_FIELD_LENGTH + " characters");
    }
    return value;
  }

  private static String json(Map<String, Object> value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to serialize capability-gap recommendation", ex);
    }
  }
}
