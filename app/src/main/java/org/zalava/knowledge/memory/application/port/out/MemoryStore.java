package org.zalava.knowledge.memory.application.port.out;

import java.util.List;
import org.zalava.knowledge.memory.domain.Memory;
import org.zalava.knowledge.memory.domain.MemoryDraft;

public interface MemoryStore {
  Memory remember(MemoryDraft draft);

  List<Memory> recent(int limit);

  List<Memory> search(String query, int limit);
}
