package org.zalava.runtime.application;

import org.zalava.control.application.AdministratorControlAuthorization;
import org.zalava.runtime.application.port.in.ManagedSeaRestart;

/** Ensures restart status and requests remain administrator-only at the application boundary. */
public final class AdministratorAuthorizedManagedSeaRestart implements ManagedSeaRestart {
  private final ManagedSeaRestart delegate;
  private final AdministratorControlAuthorization authorization;

  public AdministratorAuthorizedManagedSeaRestart(
      ManagedSeaRestart delegate, AdministratorControlAuthorization authorization) {
    this.delegate = delegate;
    this.authorization = authorization;
  }

  @Override
  public Status status() {
    return authorization.call("sea-restart-status", delegate::status);
  }

  @Override
  public Status request() {
    return authorization.call("sea-restart", delegate::request);
  }
}
