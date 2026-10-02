package org.zalava.api.extensions.managed;

/** Exact reason a module declaration exceeds its authority-bound resource grant. */
public enum ManagedServiceValidationFailure {
  OWNER_MISMATCH,
  UNDECLARED_SECRET,
  UNGRANTED_DATA_PATH,
  UNGRANTED_PORT,
  UNGRANTED_DEVICE,
  RESOURCE_LIMIT_EXCEEDED,
  READINESS_DEADLINE_EXCEEDED,
  RESTART_LIMIT_EXCEEDED
}
