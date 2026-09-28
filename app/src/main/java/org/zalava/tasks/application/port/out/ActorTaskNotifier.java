package org.zalava.tasks.application.port.out;

import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.Task;

/** Delivers a task status only to the owning actor. */
public interface ActorTaskNotifier {
  void notify(Actor actor, String taskName, Task.Status status, String feedback);
}
