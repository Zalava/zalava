package org.zalava.web.onboarding.domain;

import java.util.List;
import java.util.Map;

public record OnboardingPage(
    String stepId,
    String title,
    String templatePath,
    boolean optional,
    int stepNumber,
    int totalSteps,
    String previousStepId,
    String nextStepId,
    List<Step> steps,
    Map<String, Object> model) {
  public record Step(String title, boolean optional) {}
}
