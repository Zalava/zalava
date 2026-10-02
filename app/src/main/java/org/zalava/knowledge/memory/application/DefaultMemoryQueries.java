package org.zalava.knowledge.memory.application;

import java.util.List;
import org.zalava.knowledge.memory.application.port.in.MemoryQueries;
import org.zalava.knowledge.memory.application.port.out.MemoryStore;
import org.zalava.knowledge.memory.domain.Memory;

public final class DefaultMemoryQueries implements MemoryQueries {

  private final MemoryStore memoryStore;

  public DefaultMemoryQueries(MemoryStore memoryStore) {
    this.memoryStore = memoryStore;
  }

  @Override
  public List<Memory> recent(int limit) {
    return memoryStore.recent(limit);
  }

  @Override
  public List<Memory> search(String query, int limit) {
    return memoryStore.search(query, limit);
  }
}
