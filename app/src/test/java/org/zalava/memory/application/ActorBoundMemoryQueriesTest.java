package org.zalava.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.adapter.out.filesystem.FileSystemMemoryStore;
import org.zalava.memory.application.port.in.ActorMemoryQueries;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;

class ActorBoundMemoryQueriesTest {

  private final ActorMemoryQueries memories = mock(ActorMemoryQueries.class);
  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final ActorBoundMemoryQueries queries = new ActorBoundMemoryQueries(memories, actors);

  @Test
  void returnsNothingOutsideAnActorContext() {
    assertThat(queries.recent(3)).isEmpty();
    assertThat(queries.search("query", 3)).isEmpty();
    verifyNoInteractions(memories);
  }

  @Test
  void delegatesDurableScopedQueriesForTheCurrentActor() {
    Actor actor = new Actor(AccountId.newId());
    Memory memory = new Memory("memory-1", MemoryScope.USER, "text", Map.of(), Instant.EPOCH);
    when(memories.recent(actor, MemoryScope.durableScopes(), 50)).thenReturn(List.of(memory));

    actors.call(
        actor,
        AccountRole.MEMBER,
        () -> {
          assertThat(queries.recent(2)).containsExactly(memory);
          assertThat(queries.search("text", 2)).containsExactly(memory);
          return null;
        });

    verify(memories, times(2)).recent(actor, MemoryScope.durableScopes(), 50);
  }

  @Test
  void realFilesystemStoreInjectsOnlyTheCurrentActorsDurableMemories(@TempDir Path workspace) {
    var store = new FileSystemMemoryStore(workspace);
    var bound = new ActorBoundMemoryQueries(store, actors);
    Actor actor = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    Memory durable =
        store.remember(actor, new MemoryDraft(MemoryScope.USER, "durable fact", Map.of()));
    store.remember(
        actor,
        new MemoryDraft(
            MemoryScope.EXECUTION,
            "temporary trace",
            Map.of(),
            MemoryProvenance.of("task", "run-1")));
    store.remember(other, new MemoryDraft(MemoryScope.USER, "another actor fact", Map.of()));

    actors.call(
        actor,
        AccountRole.MEMBER,
        () -> {
          assertThat(bound.recent(10)).extracting(Memory::id).containsExactly(durable.id());
          assertThat(bound.search("fact", 10)).extracting(Memory::id).containsExactly(durable.id());
          return null;
        });

    assertThat(bound.recent(10)).isEmpty();
  }
}
