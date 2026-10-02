package org.zalava.modules.managedservices.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Durable aggregate administrator decision over one planned install. The exact planned services,
 * revisions, and grants are captured at approval time; execution may only start those resources.
 */
public record ManagedServiceInstallRequest(
    String requestId,
    Instant createdAt,
    List<ManagedServiceInstallPlanning.PlannedService> services,
    ManagedServiceInstallPlanning.AggregatedResources aggregate,
    Status status,
    Instant decidedAt,
    String message) {

  public ManagedServiceInstallRequest {
    Objects.requireNonNull(requestId, "requestId");
    if (requestId.isBlank()) throw new IllegalArgumentException("requestId must not be blank");
    Objects.requireNonNull(createdAt, "createdAt");
    services = services == null ? List.of() : List.copyOf(services);
    if (services.isEmpty()) {
      throw new IllegalArgumentException("install request must contain at least one service");
    }
    Objects.requireNonNull(aggregate, "aggregate");
    Objects.requireNonNull(status, "status");
  }

  public enum Status {
    PENDING,
    EXECUTING,
    PARTIALLY_FAILED,
    SUCCEEDED,
    FAILED,
    DENIED
  }
}
