package org.zalava.onboarding.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S1AndS6StepsTest {

  private final S1_WelcomeStep welcome = new S1_WelcomeStep();
  private final S6_CompleteStep complete = new S6_CompleteStep();

  @Test
  void welcomeStepAcceptsAnySubmissionAndExposesIdentity() {
    assertThat(welcome.getStepId()).isEqualTo("welcome");
    assertThat(welcome.getStepTitle()).isEqualTo("Welcome");
    assertThat(welcome.getTemplatePath()).isEqualTo("onboarding/steps/S1-welcome");
    assertThat(welcome.isOptional()).isFalse();

    Map<String, Object> session = new HashMap<>();
    assertThat(welcome.processStep(Map.of(), session)).isNull();
    assertThat(welcome.processStep(null, session)).isNull();
    welcome.prepareModel(null, new HashMap<>());
  }

  @Test
  void completeStepAcceptsAnySubmissionAndExposesIdentity() {
    assertThat(complete.getStepId()).isEqualTo("complete");
    assertThat(complete.getStepTitle()).isEqualTo("Complete");
    assertThat(complete.getTemplatePath()).isEqualTo("onboarding/steps/S6-complete");
    assertThat(complete.isOptional()).isFalse();

    Map<String, Object> session = new HashMap<>();
    assertThat(complete.processStep(Map.of(), session)).isNull();
  }

  @Test
  void completeStepSuppliesADefaultProviderLabelWithoutOverridingAFlashAttribute() {
    // The model the controller passes may already carry a flash "providerLabel" attribute.
    Map<String, Object> modelWithFlashLabel = new HashMap<>();
    modelWithFlashLabel.put("providerLabel", "Anthropic");
    complete.prepareModel(new HashMap<>(), modelWithFlashLabel);
    assertThat(modelWithFlashLabel.get("providerLabel")).isEqualTo("Anthropic");

    Map<String, Object> emptyModel = new HashMap<>();
    complete.prepareModel(new HashMap<>(), emptyModel);
    assertThat(emptyModel.get("providerLabel")).isEqualTo("your selected provider");
  }

  @Test
  void completeStepMarksOnboardingCompleteDuringSave() throws Exception {
    var commands =
        org.mockito.Mockito.mock(
            org.zalava.configuration.application.port.in.ConfigurationCommands.class);

    complete.saveConfiguration(new HashMap<>(), commands);

    org.mockito.Mockito.verify(commands).updateProperty("agent.onboarding.completed", true);
  }
}
