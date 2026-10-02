package org.zalava.modules.managedservices.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;

/** Durable state boundary for aggregate managed-service install decisions. */
public interface ManagedServiceInstallRequestStore {

  ManagedServiceInstallRequest save(ManagedServiceInstallRequest request);

  Optional<ManagedServiceInstallRequest> find(String requestId);

  List<ManagedServiceInstallRequest> recent(int limit);
}
