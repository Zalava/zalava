package org.zalava.managed.application;

import java.time.Clock;
import java.time.Instant;
import org.zalava.ManagedServiceAuthority;
import org.zalava.managed.ManagedServiceLifecycle;
import org.zalava.managed.ManagedServiceLifecycleResult;
import org.zalava.managed.ManagedServiceValidator;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;
import org.zalava.managed.application.port.out.OciServiceEngine;

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
      return states.save(
          record.observed(ManagedServiceObservedState.FAILED, record.observedRevision()));
    }
    try {
      if (record.desiredState().lifecycle() == ManagedServiceLifecycle.STOPPED) {
        if (actual.exists() && actual.running()) engine.stop(record.serviceId());
        return states.save(
            record.observed(ManagedServiceObservedState.STOPPED, record.desiredRevision()));
      }
      if (!actual.exists()) engine.create(record);
      if (!actual.running()) engine.start(record.serviceId());
      OciServiceEngine.Observation ready = engine.inspect(record.serviceId());
      if (ready.ready())
        return states.save(
            record.observed(ManagedServiceObservedState.RUNNING, record.desiredRevision()));
      if (record.consecutiveFailures() >= record.desiredState().restartLimit()) {
        return states.save(
            record.observed(ManagedServiceObservedState.FAILED, record.observedRevision()));
      }
      return states.save(record.failed(now.plus(record.desiredState().readinessDeadline())));
    } catch (RuntimeException ex) {
      return states.save(record.failed(now.plus(record.desiredState().readinessDeadline())));
    }
  }
}
