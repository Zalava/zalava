package org.zalava.capabilities.discovery.application.port.in;

import java.util.List;

/**
 * Read-only possible capability matches from stable catalog metadata, never installation authority.
 */
public interface ModuleSuggestions {
  Outcome suggest(String capability);

  enum Status {
    MATCH,
    NO_MATCH,
    UNAVAILABLE,
    DISABLED
  }

  record Module(String moduleId, String displayName, String description) {}

  record Outcome(Status status, List<Module> modules) {
    public Outcome {
      modules = List.copyOf(modules);
    }

    public static Outcome of(Status status) {
      return new Outcome(status, List.of());
    }
  }
}
