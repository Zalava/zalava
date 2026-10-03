package org.zalava.web.control.adapter.in.http;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries;

/** Compatibility adapter for existing local-control consumers. */
@Service
public class ZalavaBootstrapVerificationService {
  private final BootstrapVerificationQueries queries;

  public ZalavaBootstrapVerificationService(BootstrapVerificationQueries queries) {
    this.queries = queries;
  }

  public List<BootstrapToolVerification> bootstrapVerification() {
    return queries.bootstrapVerification().stream().map(BootstrapToolVerification::from).toList();
  }

  public record BootstrapToolVerification(
      String toolset,
      String providerId,
      String displayName,
      boolean available,
      String missingReason,
      List<VerificationStep> steps) {
    static BootstrapToolVerification from(
        BootstrapVerificationQueries.BootstrapToolVerification source) {
      return new BootstrapToolVerification(
          source.toolset(),
          source.providerId(),
          source.displayName(),
          source.available(),
          source.missingReason(),
          source.steps().stream().map(VerificationStep::from).toList());
    }
  }

  public record VerificationStep(
      String label,
      String method,
      String path,
      String toolName,
      boolean sideEffecting,
      boolean confirmationRequired,
      Map<String, Object> body) {
    static VerificationStep from(BootstrapVerificationQueries.VerificationStep source) {
      return new VerificationStep(
          source.label(),
          source.method(),
          source.path(),
          source.toolName(),
          source.sideEffecting(),
          source.confirmationRequired(),
          source.body());
    }
  }
}
