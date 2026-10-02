package org.zalava.assistant.agent.application.port.out;

import org.zalava.assistant.agent.domain.AgentContext;
import org.zalava.assistant.agent.domain.AgentToolSelection;

public interface AgentContextAssembler {
  AgentContext assemble(String input, AgentToolSelection toolSelection);
}
