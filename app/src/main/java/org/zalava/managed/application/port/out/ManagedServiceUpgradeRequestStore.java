package org.zalava.managed.application.port.out;

import java.util.Optional;
import org.zalava.managed.application.ManagedServiceUpgradeRequest;

/** Durable boundary for aggregate upgrade decisions and their per-service progress. */
public interface ManagedServiceUpgradeRequestStore {

  ManagedServiceUpgradeRequest save(ManagedServiceUpgradeRequest request);

  Optional<ManagedServiceUpgradeRequest> find(String requestId);

  java.util.List<ManagedServiceUpgradeRequest> recent(int limit);
}
