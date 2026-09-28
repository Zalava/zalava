package org.zalava.memory.application.port.in;

import java.util.Optional;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryScope;

/**
 * Owner-scoped management of durable memory. Ownership always comes from the authenticated actor; a
 * memory id never authorizes access. Content is re-validated on every edit.
 */
public interface MemoryManagement {

  /**
   * Revises an owned durable memory's scope and text, preserving its identity, creation time,
   * provenance and metadata.
   *
   * @throws IllegalArgumentException when the scope is not durable or the text fails policy
   */
  Optional<Memory> revise(Actor actor, String memoryId, MemoryScope scope, String text);

  /** Deletes one owned memory, returning whether it existed. */
  boolean delete(Actor actor, String memoryId);
}
