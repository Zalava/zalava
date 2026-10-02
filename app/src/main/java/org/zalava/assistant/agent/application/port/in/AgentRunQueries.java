package org.zalava.assistant.agent.application.port.in;

import java.util.List;
import org.zalava.assistant.agent.domain.AgentRun;

public interface AgentRunQueries {
  List<AgentRun> recent();
}
