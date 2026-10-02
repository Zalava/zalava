package org.zalava.api.extensions.managed;

import java.util.List;

/** Typed, engine-independent result of accepting or rejecting a requested lifecycle state. */
public sealed interface ManagedServiceLifecycleResult {

  record Accepted(ManagedServiceDesiredState desiredState)
      implements ManagedServiceLifecycleResult {}

  record Rejected(List<ManagedServiceValidationFailure> failures)
      implements ManagedServiceLifecycleResult {
    public Rejected {
      failures = List.copyOf(failures);
      if (failures.isEmpty()) {
        throw new IllegalArgumentException("rejected result must contain a failure");
      }
    }
  }
}
