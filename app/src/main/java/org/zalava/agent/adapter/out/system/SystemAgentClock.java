package org.zalava.agent.adapter.out.system;

import java.time.Instant;
import org.zalava.agent.application.port.out.AgentClock;
import org.springframework.stereotype.Component;

@Component
public final class SystemAgentClock implements AgentClock {
  @Override
  public Instant now() {
    return Instant.now();
  }
}
