package org.zalava.agent.application.port.out;

import org.zalava.agent.domain.AgentContext;
import org.zalava.agent.domain.AgentToolSelection;

public interface AgentContextAssembler {
  AgentContext assemble(String input, AgentToolSelection toolSelection);
}
