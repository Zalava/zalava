package org.zalava.tasks.application.port.in;

import java.time.LocalDateTime;
import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/** Actor-bound task commands and queries. */
public interface ActorTaskCommands {
  ActorTaskReference create(Actor actor, String name, String description);

  ActorTaskReference schedule(
      Actor actor, LocalDateTime executionTime, String name, String description);

  Task get(Actor actor, ActorTaskReference reference);

  List<ActorTaskReference> list(Actor actor);

  void resume(Actor actor, ActorTaskReference reference);
}
