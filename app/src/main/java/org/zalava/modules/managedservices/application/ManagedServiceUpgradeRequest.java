package org.zalava.modules.managedservices.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.zalava.managed.ManagedServiceDesiredState;

/**
 * Durable aggregate administrator decision over one planned upgrade set. The captured previous
 * state and data identity of every service enable deterministic rollback, and per-service phase
 * progress lets an interrupted upgrade recover on restart without repeating engine phases.
 */
public record ManagedServiceUpgradeRequest(
    String requestId,
    long generation,
    Instant createdAt,
    List<PlannedUpgrade> services,
    Status status,
    Instant decidedAt,
    String message) {

  public ManagedServiceUpgradeRequest {
    Objects.requireNonNull(requestId, "requestId");
    if (requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }
    if (generation < 1) {
      throw new IllegalArgumentException("generation must be positive");
    }
    Objects.requireNonNull(createdAt, "createdAt");
    services = services == null ? List.of() : List.copyOf(services);
    if (services.isEmpty()) {
      throw new IllegalArgumentException("upgrade request must contain at least one service");
    }
    Objects.requireNonNull(status, "status");
  }

  public enum Status {
    PENDING,
    PROMOTING,
    SUCCEEDED,
    ROLLED_BACK,
    FAILED,
    DENIED
  }

  /** Recovery-relevant progress of one service inside the aggregate upgrade. */
  public enum Phase {
    PLANNED,
    BACKED_UP,
    STOPPED,
    REMOVED,
    PROMOTING,
    PROMOTED,
    ROLLED_BACK,
    FAILED
  }

  /**
   * One planned upgrade with the captured previous state enabling rollback. The candidate must
   * already be validated against the recorded grant, and the grant itself is unchanged.
   */
  public record PlannedUpgrade(
      String moduleId,
      String serviceId,
      ManagedServiceDesiredState candidateState,
      ManagedServiceDesiredState previousState,
      String previousDataIdentity,
      Phase phase,
      String backupLocation) {

    public PlannedUpgrade {
      Objects.requireNonNull(moduleId, "moduleId");
      Objects.requireNonNull(serviceId, "serviceId");
      Objects.requireNonNull(candidateState, "candidateState");
      Objects.requireNonNull(previousState, "previousState");
      if (previousDataIdentity == null || previousDataIdentity.isBlank()) {
        throw new IllegalArgumentException("previousDataIdentity must not be blank");
      }
      phase = phase == null ? Phase.PLANNED : phase;
      backupLocation = backupLocation == null ? null : backupLocation;
    }

    public PlannedUpgrade withPhase(Phase newPhase) {
      return new PlannedUpgrade(
          moduleId,
          serviceId,
          candidateState,
          previousState,
          previousDataIdentity,
          newPhase,
          backupLocation);
    }

    public PlannedUpgrade backedUp(String location) {
      return new PlannedUpgrade(
          moduleId,
          serviceId,
          candidateState,
          previousState,
          previousDataIdentity,
          Phase.BACKED_UP,
          java.util.Objects.requireNonNullElse(location, ""));
    }
  }

  /** Optimistic-concurrency view: mutations carry the generation they were planned on. */
  public ManagedServiceUpgradeRequest withGeneration(long newGeneration) {
    return new ManagedServiceUpgradeRequest(
        requestId, newGeneration, createdAt, services, status, decidedAt, message);
  }

  public ManagedServiceUpgradeRequest withServices(List<PlannedUpgrade> newServices) {
    return new ManagedServiceUpgradeRequest(
        requestId, generation, createdAt, newServices, status, decidedAt, message);
  }

  public ManagedServiceUpgradeRequest withStatus(Status newStatus, String newMessage, Instant now) {
    Instant newDecidedAt =
        newStatus == Status.PENDING || newStatus == Status.PROMOTING ? null : now;
    return new ManagedServiceUpgradeRequest(
        requestId, generation, createdAt, services, newStatus, newDecidedAt, newMessage);
  }
}
