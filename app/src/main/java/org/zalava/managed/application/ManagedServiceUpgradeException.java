package org.zalava.managed.application;

/** Raised when an upgrade operation cannot be planned, authorized, or executed. */
public class ManagedServiceUpgradeException extends RuntimeException {

  public ManagedServiceUpgradeException(String message) {
    super(message);
  }

  public ManagedServiceUpgradeException(String message, Throwable cause) {
    super(message, cause);
  }
}
