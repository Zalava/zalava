package org.zalava.web.onboarding.steps;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.zalava.api.ModuleConfigurationStatus;
import org.zalava.api.ZalavaModule;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.web.onboarding.OnboardingProvider;

/** Host-owned optional starter selection; modules cannot contribute wizard steps. */
@Component
@Order(50)
public class S5StarterModulesStep implements OnboardingProvider {
  public static final String ID = "starters";
  private static final String SESSION_SELECTION = "onboarding.starter.module-id";
  private final ZalavaRuntime zalavaRuntime;
  private final FileSystemModuleConfigurationStore configurationStore;
  private final Set<String> curatedModuleIds;

  public S5StarterModulesStep(
      ZalavaRuntime zalavaRuntime,
      FileSystemModuleConfigurationStore configurationStore,
      @Value("${zalava.onboarding.starter-module-ids:}") List<String> starterModuleIds) {
    this.zalavaRuntime = zalavaRuntime;
    this.configurationStore = configurationStore;
    this.curatedModuleIds =
        starterModuleIds.stream()
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public String getStepId() {
    return ID;
  }

  @Override
  public String getStepTitle() {
    return "Starter modules";
  }

  @Override
  public String getTemplatePath() {
    return "onboarding/steps/S5-starters";
  }

  @Override
  public boolean isOptional() {
    return true;
  }

  @Override
  public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
    String selected = (String) session.get(SESSION_SELECTION);
    model.put(
        "starterModules",
        availableModules().stream()
            .map(
                module ->
                    new StarterModule(
                        module.descriptor().moduleId(),
                        module.descriptor().displayName(),
                        module.descriptor().description(),
                        configured(module.descriptor().moduleId())))
            .toList());
    model.put("selectedStarterModuleId", selected);
    model.put("selectedStarterConfigured", selected != null && configured(selected));
  }

  @Override
  public String processStep(Map<String, String> formParams, Map<String, Object> session) {
    String action = formParams.getOrDefault("action", "continue");
    if ("deselect".equals(action)) {
      session.remove(SESSION_SELECTION);
      return "";
    }
    if ("select".equals(action)) {
      String moduleId = formParams.get("moduleId");
      if (availableModules().stream()
          .noneMatch(module -> module.descriptor().moduleId().equals(moduleId))) {
        return "This starter module is not available in this Zalava installation.";
      }
      session.put(SESSION_SELECTION, moduleId);
      return "";
    }
    String selected = (String) session.get(SESSION_SELECTION);
    if (selected != null && !configured(selected)) {
      return "Configure the selected starter module or deselect it before continuing.";
    }
    return null;
  }

  public static boolean isSelectionUpdate(
      org.zalava.web.onboarding.domain.OnboardingSubmission submission) {
    return "".equals(submission.nextStepId());
  }

  private List<ZalavaModule> availableModules() {
    return zalavaRuntime.modules().stream()
        .filter(module -> curatedModuleIds.contains(module.descriptor().moduleId()))
        .filter(
            module ->
                module.configuration().jsonSchema().get("properties")
                        instanceof Map<?, ?> properties
                    && !properties.isEmpty())
        .toList();
  }

  private boolean configured(String moduleId) {
    ModuleConfigurationStatus status = configurationStore.status(moduleId);
    return status == ModuleConfigurationStatus.ACTIVE
        || status == ModuleConfigurationStatus.RESTART_REQUIRED;
  }

  public record StarterModule(
      String moduleId, String displayName, String description, boolean configured) {}
}
