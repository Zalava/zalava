package org.zalava.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.configuration.application.port.in.ConfigurationCommands;
import org.zalava.onboarding.OnboardingProvider;

class OnboardingWorkflowTest {
  @Test
  void exposesOrderedNavigationWithoutMvcDependencies() {
    OnboardingWorkflow workflow =
        new OnboardingWorkflow(
            List.of(step("welcome", "Welcome"), step("complete", "Complete")),
            mock(ConfigurationCommands.class));

    assertThat(workflow.page("welcome", Map.of(), Map.of()))
        .extracting(page -> page.nextStepId(), page -> page.stepNumber(), page -> page.totalSteps())
        .containsExactly("complete", 1, 2);
  }

  private static OnboardingProvider step(String id, String title) {
    return new OnboardingProvider() {
      @Override
      public String getStepId() {
        return id;
      }

      @Override
      public String getStepTitle() {
        return title;
      }

      @Override
      public String getTemplatePath() {
        return "test";
      }

      @Override
      public void prepareModel(Map<String, Object> session, Map<String, Object> model) {}

      @Override
      public String processStep(Map<String, String> form, Map<String, Object> session) {
        return null;
      }
    };
  }
}
