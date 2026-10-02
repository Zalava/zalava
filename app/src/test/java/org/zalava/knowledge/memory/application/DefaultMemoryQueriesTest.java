package org.zalava.knowledge.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.knowledge.memory.application.port.out.MemoryStore;
import org.zalava.knowledge.memory.domain.Memory;
import org.zalava.knowledge.memory.domain.MemoryScope;

class DefaultMemoryQueriesTest {

  private final MemoryStore memoryStore = mock(MemoryStore.class);
  private final DefaultMemoryQueries queries = new DefaultMemoryQueries(memoryStore);

  @Test
  void delegatesSearchAndRecentQueriesToTheOwnedStoragePort() {
    Memory memory =
        new Memory("memory-1", MemoryScope.PROJECT, "provider runtime", Map.of(), Instant.EPOCH);
    when(memoryStore.search("runtime", 3)).thenReturn(List.of(memory));
    when(memoryStore.recent(2)).thenReturn(List.of(memory));

    assertThat(queries.search("runtime", 3)).containsExactly(memory);
    assertThat(queries.recent(2)).containsExactly(memory);
    verify(memoryStore).search("runtime", 3);
    verify(memoryStore).recent(2);
  }
}
