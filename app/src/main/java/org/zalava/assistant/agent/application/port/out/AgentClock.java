package org.zalava.assistant.agent.application.port.out;

import java.time.Instant;

public interface AgentClock {
  Instant now();
}
