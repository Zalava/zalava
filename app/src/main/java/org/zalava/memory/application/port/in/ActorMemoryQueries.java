package org.zalava.memory.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryScope;

/** Actor-bound queries for private durable memories. */
public interface ActorMemoryQueries {

  /** Recent memories owned by the actor within the requested scopes, newest first. */
  List<Memory> recent(Actor actor, Set<MemoryScope> scopes, int limit);

  /** Searches the actor's memories within the requested scopes, newest first. */
  List<Memory> search(Actor actor, Set<MemoryScope> scopes, String query, int limit);

  /** Finds one owned memory by identity. */
  Optional<Memory> find(Actor actor, String memoryId);

  /** Backward-compatible convenience returning recent memories across every scope. */
  default List<Memory> recent(Actor actor, int limit) {
    return recent(actor, MemoryScope.all(), limit);
  }

  /** Backward-compatible convenience searching across every scope. */
  default List<Memory> search(Actor actor, String query, int limit) {
    return search(actor, MemoryScope.all(), query, limit);
  }
}
