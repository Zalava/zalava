package org.zalava.managed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.zalava.ManagedServiceAuthority;

/** Deterministic, fail-closed comparison of a module request against its approved grant. */
public final class ManagedServiceValidator {

  private ManagedServiceValidator() {}

  public static ManagedServiceLifecycleResult validate(
      ManagedServiceAuthority authority,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant) {
    Objects.requireNonNull(authority, "authority");
    Objects.requireNonNull(desiredState, "desiredState");
    Objects.requireNonNull(grant, "grant");
    List<ManagedServiceValidationFailure> failures = new ArrayList<>();
    if (!authority.moduleId().equals(grant.moduleId()))
      failures.add(ManagedServiceValidationFailure.OWNER_MISMATCH);
    if (!grant.secretReferences().containsAll(desiredState.secretReferences()))
      failures.add(ManagedServiceValidationFailure.UNDECLARED_SECRET);
    if (!grant.dataPaths().containsAll(desiredState.dataPaths()))
      failures.add(ManagedServiceValidationFailure.UNGRANTED_DATA_PATH);
    if (!grant.ports().containsAll(desiredState.ports()))
      failures.add(ManagedServiceValidationFailure.UNGRANTED_PORT);
    if (!grant.devices().containsAll(desiredState.devices()))
      failures.add(ManagedServiceValidationFailure.UNGRANTED_DEVICE);
    if (!grant.limits().contains(desiredState.limits()))
      failures.add(ManagedServiceValidationFailure.RESOURCE_LIMIT_EXCEEDED);
    if (desiredState.readinessDeadline().compareTo(grant.maximumReadinessDeadline()) > 0)
      failures.add(ManagedServiceValidationFailure.READINESS_DEADLINE_EXCEEDED);
    if (desiredState.restartLimit() > grant.maximumRestartLimit())
      failures.add(ManagedServiceValidationFailure.RESTART_LIMIT_EXCEEDED);
    return failures.isEmpty()
        ? new ManagedServiceLifecycleResult.Accepted(desiredState)
        : new ManagedServiceLifecycleResult.Rejected(failures);
  }
}
