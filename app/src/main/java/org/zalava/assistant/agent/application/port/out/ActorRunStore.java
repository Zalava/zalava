package org.zalava.assistant.agent.application.port.out;

import java.util.List;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.identity.accounts.domain.Actor;

/** Actor-bound persistence port for private agent-run evidence. */
public interface ActorRunStore {
  void record(Actor actor, AgentRun run);

  List<AgentRun> recent(Actor actor);
}
