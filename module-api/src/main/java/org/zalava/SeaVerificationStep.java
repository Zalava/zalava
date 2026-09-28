package org.zalava;

import java.util.Map;

public record SeaVerificationStep(
    String label,
    String method,
    String path,
    String toolName,
    boolean sideEffecting,
    boolean confirmationRequired,
    Map<String, Object> body) {
  public SeaVerificationStep {
    body = Map.copyOf(body);
  }

  public static SeaVerificationStep toolInvocation(
      String label,
      String providerId,
      String toolName,
      boolean sideEffecting,
      boolean confirmationRequired,
      Map<String, Object> body) {
    return new SeaVerificationStep(
        label,
        "POST",
        "/api/sea/providers/" + providerId + "/tools/" + toolName + "/invoke",
        toolName,
        sideEffecting,
        confirmationRequired,
        body);
  }
}
