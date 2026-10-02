package org.zalava.modules.runtime.application;

import org.zalava.modules.runtime.application.port.in.ManagedSeaRestart;
import org.zalava.web.control.application.AdministratorControlAuthorization;

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
