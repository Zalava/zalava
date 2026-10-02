package org.zalava.modules.managedservices.application.port.in;

import java.util.List;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeException;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest;

/**
 * Administrator-facing managed-service upgrade workflow: plan a candidate set, approve or deny it,
 * inspect progress, and recover interrupted upgrades. All operations reject stale generations.
 */
public interface ManagedServiceUpgrade {

  ManagedServiceUpgradeRequest plan(UpgradePlanRequest request);

  ManagedServiceUpgradeRequest get(String requestId);

  List<ManagedServiceUpgradeRequest> recent(int limit);

  ManagedServiceUpgradeRequest allow(String requestId, long expectedGeneration);

  ManagedServiceUpgradeRequest deny(String requestId, long expectedGeneration);

  /** Re-evaluates non-terminal upgrades after an application restart; idempotent. */
  void recover();

  /** One submitted candidate upgrade; the grant stays the recorded one. */
  record UpgradeCandidate(
      String moduleId, String serviceId, ManagedServiceDesiredState candidateState) {

    public UpgradeCandidate {
      if (moduleId == null || moduleId.isBlank()) {
        throw new IllegalArgumentException("moduleId must not be blank");
      }
      if (serviceId == null || serviceId.isBlank()) {
        throw new IllegalArgumentException("serviceId must not be blank");
      }
      if (candidateState == null) {
        throw new IllegalArgumentException("candidateState must not be null");
      }
    }
  }

  record UpgradePlanRequest(List<UpgradeCandidate> candidates) {

    public UpgradePlanRequest {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
  }

  /**
   * Raised inline for API-convenience; execution failures use {@link
   * ManagedServiceUpgradeException}.
   */
  final class UnknownUpgradeRequestException extends ManagedServiceUpgradeException {

    public UnknownUpgradeRequestException(String requestId) {
      super("Unknown upgrade request: " + requestId);
    }
  }
}
