package org.zalava.managed.application;

/** Non-planning install-flow failure: stale approvals, state conflicts, unknown requests. */
public class ManagedServiceInstallException extends RuntimeException {

  public ManagedServiceInstallException(String message) {
    super(message);
  }
}
