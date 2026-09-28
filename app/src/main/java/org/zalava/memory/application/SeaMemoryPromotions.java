package org.zalava.memory.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.application.port.out.ActorMemoryStore;
import org.zalava.memory.application.port.out.MemoryProposalStore;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryContentPolicy;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryScope;

/**
 * Actor-owned durable-memory promotion authority.
 *
 * <p>A model-originated proposal is persisted as {@code PENDING} after SEA validates its scope,
 * length and content safety. Nothing becomes durable memory until the owning actor approves it
 * through the SEA review surface; approval re-validates the stored content, writes the memory with
 * the proposal's provenance, and records the resulting memory identity for later revocation. Every
 * transition is appended to the proposal history and survives a restart.
 */
public final class SeaMemoryPromotions implements MemoryPromotions {

  /** Newest durable memories scanned for an existing matching record during duplicate detection. */
  public static final int DUPLICATE_WINDOW = 200;

  private final MemoryProposalStore proposals;
  private final ActorMemoryStore memories;
  private final Supplier<Instant> clock;

  public SeaMemoryPromotions(
      MemoryProposalStore proposals, ActorMemoryStore memories, Supplier<Instant> clock) {
    this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
    this.memories = Objects.requireNonNull(memories, "memories must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public synchronized MemoryProposal propose(Actor actor, MemoryProposalDraft draft) {
    requireActor(actor);
    Objects.requireNonNull(draft, "draft must not be null");
    MemoryContentPolicy.validate(draft.scope(), draft.text());
    String normalized = normalize(draft.text());
    Optional<MemoryProposal> existing =
        pending(actor).stream()
            .filter(proposal -> normalize(proposal.text()).equals(normalized))
            .findFirst();
    if (existing.isPresent()) {
      return existing.get();
    }
    boolean duplicate =
        memories.recent(actor, MemoryScope.durableScopes(), DUPLICATE_WINDOW).stream()
            .anyMatch(memory -> normalize(memory.text()).equals(normalized));
    if (duplicate) {
      throw new DuplicateMemoryException();
    }
    MemoryProposal proposal =
        MemoryProposal.pending(
            UUID.randomUUID().toString(),
            actor.accountId().toString(),
            draft,
            clock.get().toString());
    proposals.save(proposal);
    return proposal;
  }

  @Override
  public synchronized List<MemoryProposal> pending(Actor actor) {
    requireActor(actor);
    return proposals.list(actor).stream()
        .filter(proposal -> proposal.status() == MemoryProposal.Status.PENDING)
        .sorted(Comparator.comparing(MemoryProposal::createdAt).reversed())
        .toList();
  }

  @Override
  public synchronized MemoryProposal get(Actor actor, String proposalId) {
    requireActor(actor);
    return proposals.find(actor, proposalId).orElseThrow(() -> new NotFoundException(proposalId));
  }

  @Override
  public synchronized MemoryProposal approve(Actor actor, String proposalId) {
    MemoryProposal proposal = requirePending(actor, proposalId);
    MemoryContentPolicy.validate(proposal.scope(), proposal.text());
    Memory memory =
        memories.remember(
            actor,
            new MemoryDraft(
                proposal.scope(), proposal.text(), proposal.metadata(), proposal.provenance()));
    return proposals.save(proposal.approved(memory.id(), clock.get().toString()));
  }

  @Override
  public synchronized MemoryProposal reject(Actor actor, String proposalId, String reason) {
    MemoryProposal proposal = requirePending(actor, proposalId);
    return proposals.save(proposal.rejected(reason, clock.get().toString()));
  }

  @Override
  public synchronized MemoryProposal revoke(Actor actor, String proposalId) {
    MemoryProposal proposal = get(actor, proposalId);
    if (proposal.status() == MemoryProposal.Status.APPROVED && proposal.memoryId() != null) {
      memories.delete(actor, proposal.memoryId());
    }
    return proposals.save(proposal.revoked(clock.get().toString()));
  }

  private MemoryProposal requirePending(Actor actor, String proposalId) {
    MemoryProposal proposal = get(actor, proposalId);
    if (proposal.status() != MemoryProposal.Status.PENDING) {
      throw new MemoryProposal.StaleProposalException(proposalId, proposal.status());
    }
    return proposal;
  }

  private static void requireActor(Actor actor) {
    if (actor == null) {
      throw new IllegalArgumentException("A memory promotion actor is required");
    }
  }

  private static String normalize(String text) {
    return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String proposalId) {
      super("SEA memory proposal not found: " + proposalId);
    }
  }

  public static final class DuplicateMemoryException extends IllegalStateException {
    public DuplicateMemoryException() {
      super("An identical durable memory already exists");
    }
  }
}
