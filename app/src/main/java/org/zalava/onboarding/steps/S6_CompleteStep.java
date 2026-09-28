package org.zalava.onboarding.steps;

import java.io.IOException;
import java.util.Map;
import org.zalava.configuration.application.port.in.ConfigurationCommands;
import org.zalava.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
public class S6_CompleteStep implements OnboardingProvider {

  @Override
  public String getStepId() {
    return "complete";
  }

  @Override
  public String getStepTitle() {
    return "Complete";
  }

  @Override
  public String getTemplatePath() {
    return "onboarding/steps/S6-complete";
  }

  @Override
  public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
    // providerLabel is passed as a flash attribute by the controller on redirect — no session
    // needed.
    model.putIfAbsent("providerLabel", "your selected provider");
  }

  @Override
  public String processStep(Map<String, String> formParams, Map<String, Object> session) {
    return null;
  }

  @Override
  public void saveConfiguration(
      Map<String, Object> session, ConfigurationCommands configurationCommands) throws IOException {
    configurationCommands.updateProperty("agent.onboarding.completed", true);
  }
}
