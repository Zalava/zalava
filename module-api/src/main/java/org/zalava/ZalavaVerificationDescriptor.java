package org.zalava;

import java.util.List;

public record ZalavaVerificationDescriptor(
    String toolset,
    String providerId,
    List<String> requiredTools,
    List<ZalavaVerificationStep> steps) {
  public ZalavaVerificationDescriptor {
    requiredTools = List.copyOf(requiredTools);
    steps = List.copyOf(steps);
  }
}
