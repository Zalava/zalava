package org.zalava.agent.application.port.in;

import java.util.List;
import org.zalava.agent.domain.AgentRun;

public interface AgentRunQueries {
  List<AgentRun> recent();
}
