package org.zalava.modules.managedservices.adapter.out.runtime;

import java.util.List;
import java.util.Objects;
import org.zalava.managed.ManagedServiceEngine;
import org.zalava.modules.managedservices.application.ManagedServiceRecord;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/**
 * Bridges the application-owned engine port to the module-provided {@link ManagedServiceEngine}
 * service. The endpoint, socket authority, and any imperative engine handle stay SEA-owned: this
 * adapter forwards only validated records and bounded queries, and the engine is absent until a
 * module providing the contract is enabled.
 */
public final class ModuleManagedServiceEngine implements OciServiceEngine {

  private final RuntimeQueries runtime;

  public ModuleManagedServiceEngine(RuntimeQueries runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  /** The service is present only while a compatible module implementation is enabled. */
  public boolean isEngineAvailable() {
    return runtime.findService(ManagedServiceEngine.CONTRACT).isPresent();
  }

  private ManagedServiceEngine engine() {
    return runtime
        .findService(ManagedServiceEngine.CONTRACT)
        .orElseThrow(
            () ->
                new IllegalStateException("No module provides the managed-service-engine service"))
        .service();
  }

  @Override
  public Observation inspect(String serviceId) {
    ManagedServiceEngine.Observation observed = engine().inspect(serviceId);
    return new Observation(
        observed.exists(),
        observed.running(),
        observed.ready(),
        observed.ownerModuleId(),
        observed.dataIdentity());
  }

  @Override
  public void create(ManagedServiceRecord record) {
    engine()
        .create(
            new ManagedServiceEngine.Request(
                record.serviceId(),
                record.desiredState(),
                record.grant(),
                record.ownedDataIdentity()));
  }

  @Override
  public void start(String serviceId) {
    engine().start(serviceId);
  }

  @Override
  public void stop(String serviceId) {
    engine().stop(serviceId);
  }

  @Override
  public void remove(String serviceId) {
    engine().remove(serviceId);
  }

  @Override
  public List<String> recentLogs(String serviceId, int maxLines) {
    return engine().recentLogs(serviceId, maxLines);
  }
}
