package org.zalava.agent.application.port.out;

import java.time.Instant;

public interface AgentClock {
  Instant now();
}
