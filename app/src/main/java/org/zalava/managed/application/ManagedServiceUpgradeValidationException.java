package org.zalava.managed.application;

/** Raised for upgrade submissions that fail deterministic validation before any approval. */
public class ManagedServiceUpgradeValidationException extends ManagedServiceUpgradeException {

  public ManagedServiceUpgradeValidationException(String message) {
    super(message);
  }
}
