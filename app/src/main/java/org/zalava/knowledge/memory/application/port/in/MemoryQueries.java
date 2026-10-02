package org.zalava.knowledge.memory.application.port.in;

import java.util.List;
import org.zalava.knowledge.memory.domain.Memory;

public interface MemoryQueries {
  List<Memory> recent(int limit);

  List<Memory> search(String query, int limit);
}
