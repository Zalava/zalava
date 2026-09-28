package org.zalava.persistence;

/** Signals that a persisted mutable record changed after it was read. */
public final class OptimisticLockConflictException extends RuntimeException {
  public OptimisticLockConflictException(String recordType, Object id) {
    super(recordType + " was changed by another operation: " + id);
  }
}
