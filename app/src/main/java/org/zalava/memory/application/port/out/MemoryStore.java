package org.zalava.memory.application.port.out;

import java.util.List;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;

public interface MemoryStore {
  Memory remember(MemoryDraft draft);

  List<Memory> recent(int limit);

  List<Memory> search(String query, int limit);
}
