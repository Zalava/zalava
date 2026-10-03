package org.zalava.knowledge.memory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A reviewable proposal to promote model-originated content into durable memory.
 *
 * <p>The model can only create a {@link Status#PENDING} proposal. Promotion into an actor-owned
 * {@link Memory} requires an explicit owner approval through the Zalava surface; the proposal
 * records its provenance, review history and the resulting memory identity so approval, rejection
 * and revocation stay auditable and survive a restart.
 */
public record MemoryProposal(
    String id,
    String actorId,
    MemoryScope scope,
    String text,
    Map<String, String> metadata,
    MemoryProvenance provenance,
    Status status,
    String createdAt,
    String resolvedAt,
    String memoryId,
    List<Event> history) {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    REVOKED
  }

  public record Event(String type, String at, String detail) {
    public Event {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("event type must not be blank");
      }
      if (at == null || at.isBlank()) {
        throw new IllegalArgumentException("event timestamp must not be blank");
      }
      detail = detail == null ? "" : detail;
    }
  }

  public MemoryProposal {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    if (actorId == null || actorId.isBlank())
      throw new IllegalArgumentException("actorId must not be blank");
    Objects.requireNonNull(scope, "scope must not be null");
    if (text == null || text.isBlank())
      throw new IllegalArgumentException("text must not be blank");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    Objects.requireNonNull(provenance, "provenance must not be null");
    Objects.requireNonNull(status, "status must not be null");
    if (createdAt == null || createdAt.isBlank())
      throw new IllegalArgumentException("createdAt must not be blank");
    history = List.copyOf(Objects.requireNonNull(history, "history must not be null"));
    if (status != Status.PENDING && resolvedAt == null) {
      throw new IllegalArgumentException("A resolved proposal requires resolvedAt");
    }
  }

  public boolean ownedBy(String actorId) {
    return this.actorId.equals(actorId);
  }

  public static MemoryProposal pending(
      String id, String actorId, MemoryProposalDraft draft, String now) {
    return new MemoryProposal(
        id,
        actorId,
        draft.scope(),
        draft.text(),
        draft.metadata(),
        MemoryProvenance.of("proposal", draft.reference()),
        Status.PENDING,
        now,
        null,
        null,
        List.of(new Event("proposed", now, "")));
  }

  public MemoryProposal approved(String memoryId, String now) {
    requirePending(status);
    if (memoryId == null || memoryId.isBlank()) {
      throw new IllegalArgumentException("An approved proposal requires a memory identity");
    }
    return transition(Status.APPROVED, memoryId, now, new Event("approved", now, memoryId));
  }

  public MemoryProposal rejected(String reason, String now) {
    requirePending(status);
    return transition(
        Status.REJECTED, null, now, new Event("rejected", now, reason == null ? "" : reason));
  }

  public MemoryProposal revoked(String now) {
    if (status != Status.PENDING && status != Status.APPROVED) {
      throw new StaleProposalException(id, status);
    }
    return transition(Status.REVOKED, memoryId, now, new Event("revoked", now, ""));
  }

  private MemoryProposal transition(Status next, String nextMemoryId, String now, Event event) {
    List<Event> updatedHistory = new ArrayList<>(history);
    updatedHistory.add(event);
    return new MemoryProposal(
        id,
        actorId,
        scope,
        text,
        metadata,
        provenance,
        next,
        createdAt,
        now,
        nextMemoryId,
        updatedHistory);
  }

  private static void requirePending(Status status) {
    if (status != Status.PENDING) {
      throw new StaleProposalException(status);
    }
  }

  public static final class StaleProposalException extends IllegalStateException {
    private final Status status;

    public StaleProposalException(String proposalId, Status status) {
      super("Memory proposal is no longer pending: " + proposalId + " (" + status + ")");
      this.status = status;
    }

    StaleProposalException(Status status) {
      super("Memory proposal is no longer pending (" + status + ")");
      this.status = status;
    }

    public Status status() {
      return status;
    }
  }
}
