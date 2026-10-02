package org.zalava.knowledge.memory;

import java.util.List;

public interface AgentMemoryStore {

  AgentMemory remember(AgentMemoryDraft draft);

  List<AgentMemory> recent(int limit);

  List<AgentMemory> search(String query, int limit);
}
