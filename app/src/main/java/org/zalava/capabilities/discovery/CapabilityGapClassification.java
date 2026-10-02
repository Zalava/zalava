package org.zalava.capabilities.discovery;

/** Terminal outcome of one bounded remote capability lookup. */
public enum CapabilityGapClassification {
  INSTALLED_MATCH,
  DISABLED,
  NO_MATCH,
  WEAK_MATCH,
  UNAVAILABLE
}
