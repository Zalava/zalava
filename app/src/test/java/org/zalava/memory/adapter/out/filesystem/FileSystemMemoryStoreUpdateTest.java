package org.zalava.memory.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;

class FileSystemMemoryStoreUpdateTest {

  @TempDir Path workspace;

  @Test
  void revisesInPlaceAndPersistsAcrossReload() {
    Actor actor = actor();
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    Memory original =
        store.remember(
            actor,
            new MemoryDraft(
                MemoryScope.PROJECT,
                "before",
                Map.of("topic", "runtime"),
                MemoryProvenance.of("user", "run-1")));

    Optional<Memory> updated = store.update(actor, original.id(), MemoryScope.AGENT, "after");

    assertThat(updated).isPresent();
    assertThat(updated.get().id()).isEqualTo(original.id());
    assertThat(updated.get().createdAt()).isEqualTo(original.createdAt());
    assertThat(updated.get().provenance()).isEqualTo(original.provenance());
    assertThat(updated.get().metadata()).isEqualTo(Map.of("topic", "runtime"));
    assertThat(updated.get().scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(updated.get().text()).isEqualTo("after");
    assertThat(updated.get().updatedAt()).isNotNull();

    Memory reloaded = new FileSystemMemoryStore(workspace).find(actor, original.id()).orElseThrow();
    assertThat(reloaded.text()).isEqualTo("after");
    assertThat(reloaded.scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(reloaded.updatedAt()).isEqualTo(updated.get().updatedAt());
    assertThat(reloaded.provenance().source()).isEqualTo("user");
    assertThat(reloaded.metadata()).isEqualTo(Map.of("topic", "runtime"));
  }

  @Test
  void recordsWrittenBeforeARevisionLoadWithoutUpdatedAt() {
    Actor actor = actor();
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    Memory original = store.remember(actor, new MemoryDraft(MemoryScope.USER, "fact", Map.of()));

    Memory reloaded = new FileSystemMemoryStore(workspace).find(actor, original.id()).orElseThrow();

    assertThat(reloaded.updatedAt()).isNull();
  }

  @Test
  void refusesToReviseUnownedOrUnknownMemories() {
    Actor owner = actor();
    Actor other = actor();
    FileSystemMemoryStore store = new FileSystemMemoryStore(workspace);
    Memory memory = store.remember(owner, new MemoryDraft(MemoryScope.USER, "private", Map.of()));

    assertThat(store.update(other, memory.id(), MemoryScope.USER, "stolen")).isEmpty();
    assertThat(store.update(owner, "missing", MemoryScope.USER, "text")).isEmpty();
    assertThat(store.find(owner, memory.id()).orElseThrow().text()).isEqualTo("private");
  }

  private static Actor actor() {
    return new Actor(new AccountId(UUID.randomUUID()));
  }
}
