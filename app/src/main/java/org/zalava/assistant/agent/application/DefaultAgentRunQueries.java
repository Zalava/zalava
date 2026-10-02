package org.zalava.assistant.agent.application;

import java.util.List;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;

public final class DefaultAgentRunQueries implements AgentRunQueries {
  private final AgentRunStore runStore;

  public DefaultAgentRunQueries(AgentRunStore runStore) {
    this.runStore = runStore;
  }

  @Override
  public List<AgentRun> recent() {
    return runStore.recent();
  }
}
