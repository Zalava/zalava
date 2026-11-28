package org.zalava.capabilities.selfknowledge.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Bounded, deterministic substring search over descriptive fields. */
public record CapabilitySearch(String term, int limit) {
  public static final int MAX_RESULTS = 50;
  private static final int MAX_QUERY = 200;

  public CapabilitySearch {
    if (term == null || term.length() > MAX_QUERY || limit < 1 || limit > MAX_RESULTS) {
      throw new IllegalArgumentException("Invalid self-knowledge query or limit");
    }
    term = term.strip().toLowerCase(Locale.ROOT);
  }

  public boolean matches(String... fields) {
    return term.isEmpty()
        || Arrays.stream(fields)
            .filter(value -> value != null)
            .collect(Collectors.joining(" "))
            .toLowerCase(Locale.ROOT)
            .contains(term);
  }
}
