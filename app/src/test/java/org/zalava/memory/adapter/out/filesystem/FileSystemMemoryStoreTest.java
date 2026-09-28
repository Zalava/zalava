package org.zalava.memory.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryScope;

class FileSystemMemoryStoreTest {

  @TempDir Path workspace;

  @Test
  void preservesTheExistingMemoryYamlContractAcrossReload() {
    var store = new FileSystemMemoryStore(workspace);
    var saved =
        store.remember(
            new MemoryDraft(MemoryScope.PROJECT, "SEA uses providers.", Map.of("source", "test")));

    assertThat(new FileSystemMemoryStore(workspace).recent(10))
        .singleElement()
        .satisfies(
            memory -> {
              assertThat(memory.id()).isEqualTo(saved.id());
              assertThat(memory.scope()).isEqualTo(MemoryScope.PROJECT);
              assertThat(memory.text()).isEqualTo("SEA uses providers.");
              assertThat(memory.metadata()).containsEntry("source", "test");
            });
  }

  @Test
  void actorScopedMemoriesCannotBeReadByAnotherActor() {
    var store = new FileSystemMemoryStore(workspace);
    Actor first = new Actor(AccountId.newId());
    Actor second = new Actor(AccountId.newId());

    store.remember(
        first, new MemoryDraft(MemoryScope.PROJECT, "only first can read this", Map.of()));

    assertThat(store.search(first, "first", 10)).singleElement();
    assertThat(store.recent(second, 10)).isEmpty();
    assertThat(store.search(second, "first", 10)).isEmpty();
  }
}
