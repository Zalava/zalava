package org.zalava.tasks.application.port.out;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/** Actor-bound private task persistence; callers never receive a filesystem path. */
public interface ActorTaskStore {
  Task save(Actor actor, ActorTaskReference reference, Task task);

  Task get(Actor actor, ActorTaskReference reference);

  List<ActorTaskReference> list(Actor actor);
}
