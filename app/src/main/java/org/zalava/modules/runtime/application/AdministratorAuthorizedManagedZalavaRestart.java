package org.zalava.modules.runtime.application;

import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;
import org.zalava.web.control.application.AdministratorControlAuthorization;

/** Ensures restart status and requests remain administrator-only at the application boundary. */
public final class AdministratorAuthorizedManagedZalavaRestart implements ManagedZalavaRestart {
  private final ManagedZalavaRestart delegate;
  private final AdministratorControlAuthorization authorization;

  public AdministratorAuthorizedManagedZalavaRestart(
      ManagedZalavaRestart delegate, AdministratorControlAuthorization authorization) {
    this.delegate = delegate;
    this.authorization = authorization;
  }

  @Override
  public Status status() {
    return authorization.call("zalava-restart-status", delegate::status);
  }

  @Override
  public Status request() {
    return authorization.call("zalava-restart", delegate::request);
  }
}
