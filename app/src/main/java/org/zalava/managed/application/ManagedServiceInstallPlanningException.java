package org.zalava.managed.application;

/** Deterministic planning failure; carries the reject reason for administrator feedback. */
public class ManagedServiceInstallPlanningException extends RuntimeException {

  public ManagedServiceInstallPlanningException(String message) {
    super(message);
  }
}
