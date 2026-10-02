package org.zalava.knowledge.memory.application;

import java.util.Objects;
import java.util.Optional;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.memory.application.port.in.MemoryManagement;
import org.zalava.knowledge.memory.application.port.out.ActorMemoryStore;
import org.zalava.knowledge.memory.domain.Memory;
import org.zalava.knowledge.memory.domain.MemoryContentPolicy;
import org.zalava.knowledge.memory.domain.MemoryScope;

/**
 * Owner-scoped durable-memory management over the actor memory store. Edits reuse the same
 * deterministic content boundary as promotion, so a revision can never introduce secrets, oversized
 * text or a transient scope.
 */
public final class DefaultMemoryManagement implements MemoryManagement {

  private final ActorMemoryStore memories;

  public DefaultMemoryManagement(ActorMemoryStore memories) {
    this.memories = Objects.requireNonNull(memories, "memories must not be null");
  }

  @Override
  public Optional<Memory> revise(Actor actor, String memoryId, MemoryScope scope, String text) {
    Objects.requireNonNull(actor, "actor must not be null");
    Objects.requireNonNull(memoryId, "memoryId must not be null");
    Memory existing = memories.find(actor, memoryId).orElse(null);
    if (existing == null) {
      return Optional.empty();
    }
    if (!existing.scope().durable()) {
      throw new IllegalArgumentException("Transient execution memory cannot be edited");
    }
    if (scope == null || !scope.durable()) {
      throw new IllegalArgumentException("Durable memory must keep a durable scope");
    }
    MemoryContentPolicy.validate(scope, text);
    return memories.update(actor, memoryId, scope, text);
  }

  @Override
  public boolean delete(Actor actor, String memoryId) {
    Objects.requireNonNull(actor, "actor must not be null");
    return memories.delete(actor, memoryId);
  }
}
