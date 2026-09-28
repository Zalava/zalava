package org.zalava.managed.application;

import java.util.List;
import java.util.Objects;
import org.zalava.managed.application.port.in.ManagedServiceQueries;
import org.zalava.managed.application.port.out.ManagedServiceStateStore;

/** Projects durable managed-service records into the bounded per-module read model. */
public final class DefaultManagedServiceQueries implements ManagedServiceQueries {

  private final ManagedServiceStateStore states;

  public DefaultManagedServiceQueries(ManagedServiceStateStore states) {
    this.states = Objects.requireNonNull(states, "states");
  }

  @Override
  public List<ManagedServiceSummary> forModule(String moduleId) {
    if (moduleId == null || moduleId.isBlank()) {
      return List.of();
    }
    return states.findAll().stream()
        .filter(record -> moduleId.equals(record.grant().moduleId()))
        .map(
            record ->
                new ManagedServiceSummary(
                    record.serviceId(),
                    record.observedState().name(),
                    record.desiredRevision(),
                    record.observedRevision(),
                    record.desiredState().artifactReference(),
                    record.desiredState().ports(),
                    record.consecutiveFailures()))
        .toList();
  }
}
