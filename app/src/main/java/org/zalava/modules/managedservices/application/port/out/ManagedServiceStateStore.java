package org.zalava.modules.managedservices.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.modules.managedservices.application.ManagedServiceRecord;

/** Durable state boundary for Zalava-owned managed resources. */
public interface ManagedServiceStateStore {
  Optional<ManagedServiceRecord> find(String serviceId);

  /** All persisted records, ordered by service id; never null. */
  List<ManagedServiceRecord> findAll();

  ManagedServiceRecord save(ManagedServiceRecord record);
}
