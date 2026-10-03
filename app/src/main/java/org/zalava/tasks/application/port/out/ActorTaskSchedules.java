package org.zalava.tasks.application.port.out;

import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ScheduledActorTask;

public interface ActorTaskSchedules {
  List<ScheduledActorTask> list(Actor actor);
}
