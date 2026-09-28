package org.zalava.discovery;

/** Terminal outcome of one bounded remote capability lookup. */
public enum CapabilityGapClassification {
  INSTALLED_MATCH,
  DISABLED,
  NO_MATCH,
  WEAK_MATCH,
  UNAVAILABLE
}
