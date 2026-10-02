package org.zalava.assistant.agent.application.port.out;

import org.zalava.assistant.agent.domain.AgentToolSelection;

public interface AgentToolSelector {
  AgentToolSelection select(String conversationId, String input);
}
