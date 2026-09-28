package org.zalava.managed.application;

import java.time.Instant;
import java.util.Objects;
import org.zalava.managed.ManagedServiceDesiredState;
import org.zalava.managed.ManagedServiceResourceGrant;

/**
 * Durable SEA-owned reconciliation state; intent and engine observation do not overwrite each
 * other.
 */
public record ManagedServiceRecord(
    String serviceId,
    ManagedServiceDesiredState desiredState,
    String desiredRevision,
    ManagedServiceResourceGrant grant,
    String grantRevision,
    ManagedServiceObservedState observedState,
    String observedRevision,
    String ownedDataIdentity,
    int consecutiveFailures,
    Instant nextAttemptAt) {
  public ManagedServiceRecord {
    Objects.requireNonNull(serviceId, "serviceId");
    if (serviceId.isBlank()) throw new IllegalArgumentException("serviceId must not be blank");
    Objects.requireNonNull(desiredState, "desiredState");
    Objects.requireNonNull(grant, "grant");
    Objects.requireNonNull(observedState, "observedState");
    Objects.requireNonNull(ownedDataIdentity, "ownedDataIdentity");
    if (desiredRevision == null
        || desiredRevision.isBlank()
        || grantRevision == null
        || grantRevision.isBlank()) {
      throw new IllegalArgumentException("desired and grant revisions must not be blank");
    }
    if (consecutiveFailures < 0)
      throw new IllegalArgumentException("consecutiveFailures must not be negative");
  }

  public ManagedServiceRecord observed(ManagedServiceObservedState state, String revision) {
    return new ManagedServiceRecord(
        serviceId,
        desiredState,
        desiredRevision,
        grant,
        grantRevision,
        state,
        revision,
        ownedDataIdentity,
        consecutiveFailures,
        nextAttemptAt);
  }

  public ManagedServiceRecord failed(Instant retryAt) {
    return new ManagedServiceRecord(
        serviceId,
        desiredState,
        desiredRevision,
        grant,
        grantRevision,
        ManagedServiceObservedState.FAILED,
        observedRevision,
        ownedDataIdentity,
        consecutiveFailures + 1,
        retryAt);
  }
}
