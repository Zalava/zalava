package org.zalava.memory.application.port.out;

import java.util.Optional;
import java.util.Set;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.in.ActorMemoryQueries;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryScope;

/** Actor-bound persistence port for private durable memories. */
public interface ActorMemoryStore extends ActorMemoryQueries {

  /** Persists a draft owned by the actor. */
  Memory remember(Actor actor, MemoryDraft draft);

  /**
   * Revises an owned memory's scope and text in place, preserving its identity, creation time,
   * provenance and metadata. Returns the revised record, or empty when the actor owns no such id.
   */
  Optional<Memory> update(Actor actor, String memoryId, MemoryScope scope, String text);

  /** Deletes one owned memory, returning whether it existed. */
  boolean delete(Actor actor, String memoryId);

  /** Deletes every owned memory in the given scopes, returning the number removed. */
  int delete(Actor actor, Set<MemoryScope> scopes);
}
