package org.zalava.managed;

/** Bounded CPU, memory, and process-count request for one managed service. */
public record ManagedServiceLimits(long cpuMillis, long memoryBytes, int processLimit) {

  public ManagedServiceLimits {
    if (cpuMillis < 1 || memoryBytes < 1 || processLimit < 1) {
      throw new IllegalArgumentException("managed-service limits must be positive");
    }
  }

  boolean contains(ManagedServiceLimits requested) {
    return cpuMillis >= requested.cpuMillis
        && memoryBytes >= requested.memoryBytes
        && processLimit >= requested.processLimit;
  }
}
