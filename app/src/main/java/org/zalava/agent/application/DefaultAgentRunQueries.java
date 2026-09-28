package org.zalava.agent.application;

import java.util.List;
import org.zalava.agent.application.port.in.AgentRunQueries;
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.agent.domain.AgentRun;

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
