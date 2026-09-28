package org.zalava.agent.adapter.out.system;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.zalava.agent.application.port.out.AgentClock;

@Component
public final class SystemAgentClock implements AgentClock {
  @Override
  public Instant now() {
    return Instant.now();
  }
}
