package org.zalava.api.extensions.managed;

/** Requested lifecycle state. Reconciliation and engine execution are intentionally deferred. */
public enum ManagedServiceLifecycle {
  RUNNING,
  STOPPED
}
