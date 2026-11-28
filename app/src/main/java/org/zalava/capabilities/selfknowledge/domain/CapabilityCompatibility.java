package org.zalava.capabilities.selfknowledge.domain;

/** A declaration is relevant only to the exact module version for which it was recorded. */
public final class CapabilityCompatibility {
  private CapabilityCompatibility() {}

  public static boolean matchesVersion(String loadedVersion, String recordedVersion) {
    return loadedVersion != null
        && !loadedVersion.isBlank()
        && loadedVersion.equals(recordedVersion);
  }
}
