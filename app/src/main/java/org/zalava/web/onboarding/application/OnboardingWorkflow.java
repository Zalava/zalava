package org.zalava.web.onboarding.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.zalava.platform.configuration.application.port.in.ConfigurationCommands;
import org.zalava.web.onboarding.OnboardingProvider;
import org.zalava.web.onboarding.domain.OnboardingPage;
import org.zalava.web.onboarding.domain.OnboardingSubmission;

/** Framework-free ordering, navigation, validation, and completion policy. */
public final class OnboardingWorkflow {
  private static final String COMPLETE_STEP_ID = "complete";
  private final List<OnboardingProvider> steps;
  private final ConfigurationCommands configurationCommands;

  public OnboardingWorkflow(
      List<OnboardingProvider> steps, ConfigurationCommands configurationCommands) {
    this.steps = List.copyOf(steps);
    this.configurationCommands = configurationCommands;
  }

  public String firstStepId() {
    return steps.getFirst().getStepId();
  }

  public OnboardingPage page(
      String stepId, Map<String, Object> session, Map<String, Object> suppliedModel) {
    OnboardingProvider provider = provider(stepId);
    if (provider == null) return null;
    Map<String, Object> model = new HashMap<>(suppliedModel);
    provider.prepareModel(session, model);
    int index = indexOf(stepId);
    return new OnboardingPage(
        stepId,
        provider.getStepTitle(),
        provider.getTemplatePath(),
        provider.isOptional(),
        index + 1,
        steps.size(),
        index > 0 ? steps.get(index - 1).getStepId() : null,
        index < steps.size() - 1 ? steps.get(index + 1).getStepId() : null,
        steps.stream()
            .map(step -> new OnboardingPage.Step(step.getStepTitle(), step.isOptional()))
            .toList(),
        model);
  }

  public OnboardingSubmission submit(
      String stepId, Map<String, String> form, Map<String, Object> session) {
    OnboardingProvider provider = provider(stepId);
    if (provider == null) return new OnboardingSubmission(stepId, firstStepId(), null);
    String error = provider.processStep(form, session);
    return new OnboardingSubmission(stepId, error == null ? nextStepId(stepId) : stepId, error);
  }

  public void save(Map<String, Object> session) {
    try {
      for (OnboardingProvider step : steps) step.saveConfiguration(session, configurationCommands);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to save onboarding configuration", ex);
    }
  }

  private OnboardingProvider provider(String stepId) {
    return steps.stream().filter(step -> step.getStepId().equals(stepId)).findFirst().orElse(null);
  }

  private int indexOf(String stepId) {
    for (int i = 0; i < steps.size(); i++) if (steps.get(i).getStepId().equals(stepId)) return i;
    return 0;
  }

  private String nextStepId(String stepId) {
    int index = indexOf(stepId);
    return index < steps.size() - 1 ? steps.get(index + 1).getStepId() : null;
  }
}
