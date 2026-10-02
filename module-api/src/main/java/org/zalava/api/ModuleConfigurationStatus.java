package org.zalava.api;

/** Stable lifecycle status for one module's host-owned configuration. */
public enum ModuleConfigurationStatus {
  SETUP_REQUIRED,
  RESTART_REQUIRED,
  ACTIVE,
  CONFIGURATION_INVALID,
  ACTIVATION_FAILED
}
