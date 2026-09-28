package org.zalava.managed;

/** Requested lifecycle state. Reconciliation and engine execution are intentionally deferred. */
public enum ManagedServiceLifecycle {
  RUNNING,
  STOPPED
}
