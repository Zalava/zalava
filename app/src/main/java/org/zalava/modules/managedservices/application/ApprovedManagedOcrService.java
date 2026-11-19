package org.zalava.modules.managedservices.application;

import java.util.Optional;
import java.util.function.Supplier;
import org.zalava.api.extensions.managed.ManagedOcrService;
import org.zalava.api.extensions.managed.ManagedServiceLifecycle;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

/** Uses only the approved, running isolated worker owned by the requesting module. */
public final class ApprovedManagedOcrService implements ManagedOcrService {
  private final String moduleId;
  private final ManagedServiceStateStore states;
  private final Supplier<OciServiceEngine> engine;

  public ApprovedManagedOcrService(
      String moduleId, ManagedServiceStateStore states, Supplier<OciServiceEngine> engine) {
    this.moduleId = moduleId;
    this.states = states;
    this.engine = engine;
  }

  @Override
  public Optional<String> extract(Request request) {
    return states
        .find(SERVICE_ID)
        .filter(this::approvedAndReady)
        .flatMap(record -> engine.get().extractOcr(record, request));
  }

  private boolean approvedAndReady(ManagedServiceRecord record) {
    return moduleId.equals(record.grant().moduleId())
        && record.desiredState().networkIsolated()
        && record.desiredState().lifecycle() == ManagedServiceLifecycle.RUNNING
        && record.observedState() == ManagedServiceObservedState.RUNNING
        && record.desiredRevision().equals(record.observedRevision());
  }
}
