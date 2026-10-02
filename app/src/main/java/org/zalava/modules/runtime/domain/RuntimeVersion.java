package org.zalava.modules.runtime.domain;

/** Comparable semantic runtime version used for module compatibility checks. */
public record RuntimeVersion(String value, int major, int minor, int patch)
    implements Comparable<RuntimeVersion> {

  public static RuntimeVersion parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
    String[] parts = value.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("version must use major.minor.patch format");
    }
    try {
      return new RuntimeVersion(
          value,
          Integer.parseInt(parts[0]),
          Integer.parseInt(parts[1]),
          Integer.parseInt(parts[2]));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("version must use numeric components", ex);
    }
  }

  @Override
  public int compareTo(RuntimeVersion other) {
    int majorComparison = Integer.compare(major, other.major);
    if (majorComparison != 0) return majorComparison;
    int minorComparison = Integer.compare(minor, other.minor);
    return minorComparison != 0 ? minorComparison : Integer.compare(patch, other.patch);
  }
}
