package org.zalava.managed.application.port.in;

import java.time.Instant;
import java.util.List;
import org.zalava.managed.application.ManagedServiceUpgradeException;

/**
 * Bounded administrator diagnostics over installed managed services: inspect durable state, read
 * engine logs within a hard cap, and force a reconciliation of a running service.
 */
public interface ManagedServiceDiagnostics {

  DiagnosticsReport inspect(String serviceId);

  List<String> recentLogs(String serviceId, int maximumLines);

  /** Forces a reconciliation attempt, clearing reconciler backoff; fails when still not running. */
  DiagnosticsReport restart(String serviceId);

  record DiagnosticsReport(
      String serviceId,
      String moduleId,
      String artifactReference,
      String desiredRevision,
      String grantRevision,
      String observedState,
      String observedRevision,
      int consecutiveFailures,
      Instant nextAttemptAt) {}

  final class UnknownServiceException extends ManagedServiceUpgradeException {

    public UnknownServiceException(String serviceId) {
      super("Unknown managed service: " + serviceId);
    }
  }
}
