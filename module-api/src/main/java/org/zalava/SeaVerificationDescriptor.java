package org.zalava;

import java.util.List;

public record SeaVerificationDescriptor(
    String toolset,
    String providerId,
    List<String> requiredTools,
    List<SeaVerificationStep> steps) {
  public SeaVerificationDescriptor {
    requiredTools = List.copyOf(requiredTools);
    steps = List.copyOf(steps);
  }
}
