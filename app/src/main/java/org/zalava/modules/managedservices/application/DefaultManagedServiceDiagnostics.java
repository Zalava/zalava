package org.zalava.modules.managedservices.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.zalava.ManagedServiceAuthority;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceDiagnostics;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

/**
 * Bounded, read-mostly administrator diagnostics: durable state inspection, engine logs within a
 * hard cap, and a forced reconciliation that clears reconciler backoff. Engine access stays behind
 * the application-owned ports; nothing here exposes imperative engine handles.
 */
public final class DefaultManagedServiceDiagnostics implements ManagedServiceDiagnostics {

  private static final int MAX_LOG_LINES = 500;

  private final ManagedServiceStateStore states;
  private final ManagedServiceReconciler reconciler;
  private final OciServiceEngine engine;
  private final Clock clock;

  public DefaultManagedServiceDiagnostics(
      ManagedServiceStateStore states,
      ManagedServiceReconciler reconciler,
      OciServiceEngine engine,
      Clock clock) {
    this.states = Objects.requireNonNull(states, "states");
    this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public DiagnosticsReport inspect(String serviceId) {
    return report(load(serviceId));
  }

  @Override
  public List<String> recentLogs(String serviceId, int maximumLines) {
    ManagedServiceRecord record = load(serviceId);
    int bounded = Math.min(Math.max(0, maximumLines), MAX_LOG_LINES);
    if (bounded == 0) {
      return List.of();
    }
    return engine.recentLogs(serviceId, bounded);
  }

  @Override
  public DiagnosticsReport restart(String serviceId) {
    ManagedServiceRecord record = load(serviceId);
    if (record.desiredState().lifecycle() != ManagedServiceLifecycle.RUNNING) {
      throw new ManagedServiceUpgradeException(
          "Managed service '"
              + serviceId
              + "' is desired stopped; start it through the workflow that stopped it");
    }
    if (record.observedState() == ManagedServiceObservedState.RUNNING) {
      return report(record); // Idempotent: already running means nothing to restart.
    }
    ManagedServiceDesiredState previous = record.desiredState();
    ManagedServiceRecord target =
        new ManagedServiceRecord(
            record.serviceId(),
            previous,
            record.desiredRevision(),
            record.grant(),
            record.grantRevision(),
            record.observedState(),
            record.observedRevision(),
            record.ownedDataIdentity(),
            0,
            null);
    ManagedServiceRecord reconciled =
        reconciler.reconcile(authority(record.grant().moduleId()), target);
    ManagedServiceRecord current = states.find(serviceId).orElse(reconciled);
    if (current.observedState() != ManagedServiceObservedState.RUNNING) {
      throw new ManagedServiceUpgradeException(
          "Managed service '"
              + serviceId
              + "' is still "
              + current.observedState()
              + " after the restart attempt");
    }
    return report(current);
  }

  private DiagnosticsReport report(ManagedServiceRecord record) {
    return new DiagnosticsReport(
        record.serviceId(),
        record.grant().moduleId(),
        record.desiredState().artifactReference(),
        record.desiredRevision(),
        record.grantRevision(),
        record.observedState().name(),
        record.observedRevision(),
        record.consecutiveFailures(),
        record.nextAttemptAt());
  }

  private ManagedServiceRecord load(String serviceId) {
    return states.find(serviceId).orElseThrow(() -> new UnknownServiceException(serviceId));
  }

  private static ManagedServiceAuthority authority(String moduleId) {
    return new ZalavaServiceFactoryContext(moduleId, Map.of(), Map.of()).managedServiceAuthority();
  }
}
