package org.zalava.tasks.application.port.out;

import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/** Executes one actor-owned task without accepting a filesystem location. */
public interface ActorTaskAgent {
  Result execute(Actor actor, ActorTaskReference reference, String prompt);

  record Result(Task.Status newStatus, String feedback) {}
}
