package org.zalava.control.application.port.in;

import java.util.List;
import java.util.Map;

public interface BootstrapVerificationQueries {

  List<BootstrapToolVerification> bootstrapVerification();

  record BootstrapToolVerification(
      String toolset,
      String providerId,
      String displayName,
      boolean available,
      String missingReason,
      List<VerificationStep> steps) {}

  record VerificationStep(
      String label,
      String method,
      String path,
      String toolName,
      boolean sideEffecting,
      boolean confirmationRequired,
      Map<String, Object> body) {}
}
