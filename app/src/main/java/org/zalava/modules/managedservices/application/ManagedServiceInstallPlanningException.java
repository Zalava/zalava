package org.zalava.modules.managedservices.application;

/** Deterministic planning failure; carries the reject reason for administrator feedback. */
public class ManagedServiceInstallPlanningException extends RuntimeException {

  public ManagedServiceInstallPlanningException(String message) {
    super(message);
  }
}
