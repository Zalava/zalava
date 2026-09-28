package org.zalava.agent.application.port.out;

import org.zalava.agent.domain.AgentToolSelection;

public interface AgentToolSelector {
  AgentToolSelection select(String conversationId, String input);
}
