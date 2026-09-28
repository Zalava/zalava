package org.zalava.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryContentPolicy;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultMemoryManagementTest {

  @TempDir Path workspace;

  private final Actor actor = actor();

  @Test
  void revisesAndDeletesOwnedDurableMemories() {
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    DefaultMemoryManagement management = new DefaultMemoryManagement(store);
    Memory memory = store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "before", Map.of()));

    Memory revised =
        management.revise(actor, memory.id(), MemoryScope.AGENT, "after").orElseThrow();

    assertThat(revised.text()).isEqualTo("after");
    assertThat(revised.scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(revised.updatedAt()).isNotNull();

    assertThat(management.delete(actor, memory.id())).isTrue();
    assertThat(store.find(actor, memory.id())).isEmpty();
    assertThat(management.delete(actor, memory.id())).isFalse();
  }

  @Test
  void refusesUnownedAndUnknownMemories() {
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    DefaultMemoryManagement management = new DefaultMemoryManagement(store);
    Memory memory = store.remember(actor, new MemoryDraft(MemoryScope.USER, "private", Map.of()));

    assertThat(management.revise(actor(), memory.id(), MemoryScope.USER, "stolen")).isEmpty();
    assertThat(management.revise(actor, "missing", MemoryScope.USER, "text")).isEmpty();
    assertThat(management.delete(actor(), memory.id())).isFalse();
    assertThat(store.find(actor, memory.id()).orElseThrow().text()).isEqualTo("private");
  }

  @Test
  void refusesTransientScopesAndUnsafeContent() {
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    DefaultMemoryManagement management = new DefaultMemoryManagement(store);
    Memory transientMemory =
        store.remember(actor, new MemoryDraft(MemoryScope.EXECUTION, "trace", Map.of()));
    Memory durable = store.remember(actor, new MemoryDraft(MemoryScope.USER, "fact", Map.of()));

    assertThatThrownBy(
            () -> management.revise(actor, transientMemory.id(), MemoryScope.USER, "edited"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Transient execution memory");

    assertThatThrownBy(
            () -> management.revise(actor, durable.id(), MemoryScope.EXECUTION, "edited"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("durable scope");

    assertThatThrownBy(
            () ->
                management.revise(actor, durable.id(), MemoryScope.USER, "the password is hunter2"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);

    assertThatThrownBy(
            () -> management.revise(actor, durable.id(), MemoryScope.USER, "x".repeat(2001)))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);

    assertThat(store.find(actor, durable.id()).orElseThrow().text()).isEqualTo("fact");
  }

  private static Actor actor() {
    return new Actor(new AccountId(UUID.randomUUID()));
  }
}
