package org.zalava.modules.managedservices.application.port.in;

import java.util.List;
import java.util.Set;
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;

/** Administrator-facing managed-service install workflow: plan, inspect, approve, deny. */
public interface ManagedServiceInstallation {

  ManagedServiceInstallRequest plan(PlanRequest request);

  ManagedServiceInstallRequest get(String requestId);

  List<ManagedServiceInstallRequest> recent(int limit);

  ManagedServiceInstallRequest allow(String requestId);

  ManagedServiceInstallRequest deny(String requestId);

  /** One submitted service; SEA binds the module authority from the submitted module id. */
  record PlannedRequest(
      String moduleId,
      String serviceId,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant,
      Set<String> dependsOn) {

    public PlannedRequest {
      dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }
  }

  record PlanRequest(List<PlannedRequest> requests) {

    public PlanRequest {
      requests = requests == null ? List.of() : List.copyOf(requests);
    }
  }
}
