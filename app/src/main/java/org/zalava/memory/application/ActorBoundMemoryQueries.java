package org.zalava.memory.application;

import java.util.List;
import java.util.Objects;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.in.ActorMemoryQueries;
import org.zalava.memory.application.port.in.MemoryQueries;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryScope;

/**
 * Actor-bound implementation of the legacy context-injection {@link MemoryQueries} port.
 *
 * <p>Context assembly has no actor parameter, so this adapter resolves the trusted actor from the
 * {@link ActorExecutionContext} established at the request or task boundary. It only exposes the
 * actor's own durable scopes; execution memory is never injected into a later turn, and callers
 * outside an actor context see nothing. Candidate retrieval is bounded by a candidate window, then
 * ranked and bounded deterministically by {@link DeterministicMemorySelector}.
 */
public final class ActorBoundMemoryQueries implements MemoryQueries {

  /** Default number of newest matches considered before relevance ranking. */
  public static final int DEFAULT_CANDIDATE_WINDOW = 50;

  private final ActorMemoryQueries memories;
  private final ActorExecutionContext actors;
  private final DeterministicMemorySelector selector;
  private final int candidateWindow;

  public ActorBoundMemoryQueries(ActorMemoryQueries memories, ActorExecutionContext actors) {
    this(memories, actors, new DeterministicMemorySelector(), DEFAULT_CANDIDATE_WINDOW);
  }

  public ActorBoundMemoryQueries(
      ActorMemoryQueries memories,
      ActorExecutionContext actors,
      DeterministicMemorySelector selector,
      int candidateWindow) {
    this.memories = Objects.requireNonNull(memories, "memories must not be null");
    this.actors = Objects.requireNonNull(actors, "actors must not be null");
    this.selector = Objects.requireNonNull(selector, "selector must not be null");
    if (candidateWindow < 1) {
      throw new IllegalArgumentException("candidateWindow must be positive");
    }
    this.candidateWindow = candidateWindow;
  }

  @Override
  public List<Memory> recent(int limit) {
    return selection(null, limit).selected();
  }

  @Override
  public List<Memory> search(String query, int limit) {
    return selection(query, limit).selected();
  }

  /** Deterministic selection for the current actor, including metrics for recorded evidence. */
  public MemorySelection selection(String query, int limit) {
    return actors
        .currentPrincipal()
        .map(principal -> selectFor(principal.actor(), query, limit))
        .orElseGet(
            () -> new MemorySelection(List.of(), new MemorySelection.Metrics(0, 0, 0, 0, 0, 0, 0)));
  }

  private MemorySelection selectFor(Actor actor, String query, int limit) {
    List<Memory> candidates = memories.recent(actor, MemoryScope.durableScopes(), candidateWindow);
    return selector.select(query, candidates, limit);
  }
}
