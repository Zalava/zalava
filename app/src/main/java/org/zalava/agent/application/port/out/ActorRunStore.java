package org.zalava.agent.application.port.out;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.domain.AgentRun;

/** Actor-bound persistence port for private agent-run evidence. */
public interface ActorRunStore {
  void record(Actor actor, AgentRun run);

  List<AgentRun> recent(Actor actor);
}
