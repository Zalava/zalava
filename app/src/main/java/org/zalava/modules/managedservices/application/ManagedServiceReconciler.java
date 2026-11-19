package org.zalava.modules.managedservices.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.zalava.api.ManagedServiceAuthority;
import org.zalava.api.extensions.managed.ManagedServiceLifecycle;
import org.zalava.api.extensions.managed.ManagedServiceLifecycleResult;
import org.zalava.api.extensions.managed.ManagedServiceValidator;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

/** Reconciles approved desired state without exposing engine commands to modules. */
public final class ManagedServiceReconciler {
  private final ManagedServiceStateStore states;
  private final OciServiceEngine engine;
  private final Clock clock;

  public ManagedServiceReconciler(
      ManagedServiceStateStore states, OciServiceEngine engine, Clock clock) {
    this.states = states;
    this.engine = engine;
    this.clock = clock;
  }

  public ManagedServiceRecord reconcile(
      ManagedServiceAuthority authority, ManagedServiceRecord record) {
    if (ManagedServiceValidator.validate(authority, record.desiredState(), record.grant())
        instanceof ManagedServiceLifecycleResult.Rejected rejected) {
      throw new IllegalStateException("managed-service grant rejected: " + rejected.failures());
    }
    Instant now = clock.instant();
    if (record.nextAttemptAt() != null && now.isBefore(record.nextAttemptAt()))
      return states.save(record);
    OciServiceEngine.Observation actual = engine.inspect(record.serviceId());
    if (actual.exists()
        && (!authority.moduleId().equals(actual.ownerModuleId())
            || !record.ownedDataIdentity().equals(actual.dataIdentity()))) {
      return states.save(record.failed(null, "Another managed service owns this service name."));
    }
    try {
      if (record.desiredState().lifecycle() == ManagedServiceLifecycle.STOPPED) {
        if (actual.exists() && actual.running()) engine.stop(record.serviceId());
        return states.save(
            record.observed(ManagedServiceObservedState.STOPPED, record.desiredRevision()));
      }
      if (record.desiredState().networkIsolated()) {
        checkIsolationSupport(record.serviceId(), actual.exists() && actual.running());
      }
      if (!actual.exists()) engine.create(record);
      if (record.desiredState().networkIsolated()) {
        checkIsolation(record.serviceId(), actual.exists() && actual.running());
      }
      if (!actual.running()) engine.start(record.serviceId());
      if (record.desiredState().networkIsolated()) {
        checkIsolation(record.serviceId(), true);
      }
      OciServiceEngine.Observation ready =
          record.desiredState().networkIsolated()
              ? awaitIsolatedReadiness(record)
              : engine.inspect(record.serviceId());
      if (ready.ready())
        return states.save(
            record.observed(ManagedServiceObservedState.RUNNING, record.desiredRevision()));
      if (record.consecutiveFailures() >= record.desiredState().restartLimit()) {
        return states.save(
            record.observed(ManagedServiceObservedState.FAILED, record.observedRevision()));
      }
      return states.save(
          record.failed(
              now.plus(record.desiredState().readinessDeadline()),
              "The service did not become ready in time."));
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      String reason =
          message != null
                  && message.startsWith(
                      "A foreign container occupies the deterministic Zalava name:")
              ? "Another container already uses this service name. Resolve the name conflict before retrying."
              : "The service could not be started. Check the service and Docker settings before retrying.";
      return states.save(
          record.failed(now.plus(record.desiredState().readinessDeadline()), reason));
    }
  }

  private OciServiceEngine.Observation awaitIsolatedReadiness(ManagedServiceRecord record) {
    String serviceId = record.serviceId();
    long deadline = System.nanoTime() + record.desiredState().readinessDeadline().toNanos();
    OciServiceEngine.Observation state = engine.inspect(serviceId);
    while (state.running() && !state.ready()) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return state;
      try {
        TimeUnit.NANOSECONDS.sleep(Math.min(remaining, Duration.ofMillis(250).toNanos()));
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for the isolated service", exception);
      }
      state = engine.inspect(serviceId);
    }
    if (state.ready()) {
      checkIsolation(serviceId, state.running());
    }
    return state;
  }

  private void checkIsolationSupport(String serviceId, boolean running) {
    try {
      if (!engine.supportsNetworkIsolation()) {
        throw new IllegalStateException("Managed-service engine cannot enforce network isolation");
      }
    } catch (RuntimeException ex) {
      stopAfterFailedIsolationCheck(serviceId, running, ex);
      throw ex;
    }
  }

  private void checkIsolation(String serviceId, boolean running) {
    try {
      if (!engine.isNetworkIsolated(serviceId)) {
        throw new IllegalStateException("Managed service is not network isolated");
      }
    } catch (RuntimeException ex) {
      stopAfterFailedIsolationCheck(serviceId, running, ex);
      throw ex;
    }
  }

  private void stopAfterFailedIsolationCheck(
      String serviceId, boolean running, RuntimeException failure) {
    if (!running) return;
    try {
      engine.stop(serviceId);
    } catch (RuntimeException stopFailure) {
      failure.addSuppressed(stopFailure);
    }
  }
}
