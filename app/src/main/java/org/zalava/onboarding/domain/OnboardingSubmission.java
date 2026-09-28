package org.zalava.onboarding.domain;

public record OnboardingSubmission(String stepId, String nextStepId, String error) {
  public boolean isAccepted() {
    return error == null;
  }

  public boolean completesOnboarding() {
    return isAccepted() && "complete".equals(nextStepId);
  }
}
