package org.zalava.modules.development.adapter.in.agent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import tools.jackson.databind.ObjectMapper;

/** Recommendation-only agent adapter for a deterministic missing Zalava capability. */
public final class CapabilityGapTools {

  private static final int MAX_FIELD_LENGTH = 2_000;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ModuleSuggestions suggestions;
  private final BooleanSupplier administrator;

  public CapabilityGapTools() {
    this(
        capability -> ModuleSuggestions.Outcome.of(ModuleSuggestions.Status.DISABLED), () -> false);
  }

  public CapabilityGapTools(ModuleSuggestions suggestions, BooleanSupplier administrator) {
    this.suggestions = Objects.requireNonNull(suggestions);
    this.administrator = Objects.requireNonNull(administrator);
  }

  @Tool(
      name = "recommendModuleDevelopment",
      description =
          "Searches the module catalog for possible existing capabilities after installed provider-tool discovery found no match. Read-only: never installs or grants permission. Only a successful no-match returns a reviewable development recommendation; explicit user confirmation is still required to create the authoritative request.")
  public String recommend(
      @ToolParam(description = "Concise missing capability") String capability,
      @ToolParam(description = "The user's bounded request that had no Zalava provider-tool match")
          String userRequest) {
    Map<String, Object> recommendation = new LinkedHashMap<>();
    String query = required(capability, "capability");
    required(userRequest, "userRequest");
    ModuleSuggestions.Outcome outcome = suggestions.suggest(query);
    recommendation.put("catalogStatus", outcome.status().name());
    recommendation.put("automaticActions", List.of());
    if (outcome.status() == ModuleSuggestions.Status.MATCH) {
      boolean admin = administrator.getAsBoolean();
      recommendation.put("status", "CATALOG_MATCH");
      recommendation.put("capability", query);
      recommendation.put(
          "matching",
          "Possible matches based on untrusted catalog metadata; verify capabilities, releases, compatibility and permissions before installation.");
      recommendation.put(
          "modules",
          outcome.modules().stream()
              .map(
                  module -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("moduleId", module.moduleId());
                    entry.put("displayName", module.displayName());
                    entry.put("description", module.description());
                    if (admin)
                      entry.put(
                          "reviewUrl",
                          "/modules?q="
                              + URLEncoder.encode(module.moduleId(), StandardCharsets.UTF_8));
                    return entry;
                  })
              .toList());
      recommendation.put(
          "nextAction",
          admin
              ? "As an administrator, open Modules, use Check catalog, review the suggested module and release, then choose Install explicitly if suitable. Installation and loading have not happened."
              : "Ask an administrator to review these possible modules in Modules and install and load a suitable release. You cannot install modules from your member account.");
      return json(recommendation);
    }
    if (outcome.status() != ModuleSuggestions.Status.NO_MATCH) {
      recommendation.put(
          "status",
          outcome.status() == ModuleSuggestions.Status.DISABLED
              ? "CATALOG_DISABLED"
              : "CATALOG_UNAVAILABLE");
      recommendation.put(
          "nextAction",
          "Catalog search did not establish whether a matching module exists. Ask an administrator to check catalog access and retry before considering module development.");
      return json(recommendation);
    }
    recommendation.put("status", "REVIEW_REQUIRED");
    recommendation.put("capability", required(capability, "capability"));
    recommendation.put("userRequest", required(userRequest, "userRequest"));
    recommendation.put("recommendedWorkflow", "manual_module_development");
    recommendation.put(
        "nextAction", "Ask the user whether to create a reviewable module-development request.");
    recommendation.put("confirmationRequired", true);
    recommendation.put("automaticActions", List.of());
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
