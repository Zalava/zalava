package org.zalava.modules.managedservices.application;

/** Engine-observed lifecycle retained separately from the module's desired state. */
public enum ManagedServiceObservedState {
  ABSENT,
  STARTING,
  RUNNING,
  STOPPED,
  FAILED
}
