package org.zalava.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.agent.domain.AgentRun;

public class InMemoryAgentRunRecorder implements AgentRunStore {

  private static final int DEFAULT_LIMIT = 200;

  private final int limit;
  private final Deque<AgentRun> records = new ArrayDeque<>();

  public InMemoryAgentRunRecorder() {
    this(DEFAULT_LIMIT);
  }

  InMemoryAgentRunRecorder(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
    this.limit = limit;
  }

  @Override
  public synchronized void record(AgentRun record) {
    records.addLast(record);
    while (records.size() > limit) {
      records.removeFirst();
    }
  }

  @Override
  public synchronized List<AgentRun> recent() {
    return List.copyOf(new ArrayList<>(records));
  }
}
