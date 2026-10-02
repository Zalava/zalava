package org.zalava.modules.runtime.domain;

/** Inclusive-minimum, optional-exclusive-maximum SEA runtime compatibility range. */
public record RuntimeCompatibility(RuntimeVersion minimum, RuntimeVersion exclusiveMaximum) {

  public static RuntimeCompatibility parse(String value) {
    String[] bounds = value.trim().split("\\s+");
    if (bounds.length < 1 || bounds.length > 2 || !bounds[0].startsWith(">=")) {
      throw new IllegalArgumentException("must use >=minimum optionally followed by <maximum");
    }
    RuntimeVersion minimum = RuntimeVersion.parse(bounds[0].substring(2).trim());
    if (bounds.length == 1) return new RuntimeCompatibility(minimum, null);
    if (!bounds[1].startsWith("<")) {
      throw new IllegalArgumentException("must use >=minimum optionally followed by <maximum");
    }
    RuntimeVersion maximum = RuntimeVersion.parse(bounds[1].substring(1).trim());
    if (minimum.compareTo(maximum) >= 0) {
      throw new IllegalArgumentException("minimum must be lower than upper bound");
    }
    return new RuntimeCompatibility(minimum, maximum);
  }

  public boolean supports(RuntimeVersion runtime) {
    return minimum.compareTo(runtime) <= 0
        && (exclusiveMaximum == null || runtime.compareTo(exclusiveMaximum) < 0);
  }
}
