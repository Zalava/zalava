package org.zalava.assistant.agent.application.port.out;

import java.util.List;
import org.zalava.assistant.agent.domain.AgentRun;

public interface AgentRunStore {
  void record(AgentRun run);

  List<AgentRun> recent();
}
