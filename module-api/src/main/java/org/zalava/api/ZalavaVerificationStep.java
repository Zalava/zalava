package org.zalava.api;

import java.util.Map;

public record ZalavaVerificationStep(
    String label,
    String method,
    String path,
    String toolName,
    boolean sideEffecting,
    boolean confirmationRequired,
    Map<String, Object> body) {
  public ZalavaVerificationStep {
    body = Map.copyOf(body);
  }

  public static ZalavaVerificationStep toolInvocation(
      String label,
      String providerId,
      String toolName,
      boolean sideEffecting,
      boolean confirmationRequired,
      Map<String, Object> body) {
    return new ZalavaVerificationStep(
        label,
        "POST",
        "/api/sea/providers/" + providerId + "/tools/" + toolName + "/invoke",
        toolName,
        sideEffecting,
        confirmationRequired,
        body);
  }
}
