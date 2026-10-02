package org.zalava.api.extensions.tasks;

import java.util.Objects;

/** Stable, host-independent reference to a persisted task. */
public record TaskReference(String value) {
  public TaskReference {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) throw new IllegalArgumentException("value must not be blank");
  }
}
