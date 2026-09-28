package org.zalava.agent.application.port.out;

import java.util.List;
import org.zalava.agent.domain.AgentRun;

public interface AgentRunStore {
  void record(AgentRun run);

  List<AgentRun> recent();
}
