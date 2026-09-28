package org.zalava.agent.adapter.out.system;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.zalava.agent.application.port.out.AgentRunIdGenerator;

@Component
public final class UuidAgentRunIdGenerator implements AgentRunIdGenerator {
  @Override
  public String nextId() {
    return UUID.randomUUID().toString();
  }
}
