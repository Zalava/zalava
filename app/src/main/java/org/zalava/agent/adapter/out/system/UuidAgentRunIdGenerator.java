package org.zalava.agent.adapter.out.system;

import java.util.UUID;
import org.zalava.agent.application.port.out.AgentRunIdGenerator;
import org.springframework.stereotype.Component;

@Component
public final class UuidAgentRunIdGenerator implements AgentRunIdGenerator {
  @Override
  public String nextId() {
    return UUID.randomUUID().toString();
  }
}
