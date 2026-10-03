package org.zalava.tasks.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorJobEvidenceQueries;
import org.zalava.tasks.application.port.out.ActorTaskSchedules;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.zalava.tasks.domain.TaskScheduleUnavailableException;

public final class DefaultActorJobEvidenceQueries implements ActorJobEvidenceQueries {
  private final ActorTaskStore tasks;
  private final ActorTaskSchedules schedules;

  public DefaultActorJobEvidenceQueries(ActorTaskStore tasks, ActorTaskSchedules schedules) {
    this.tasks = tasks;
    this.schedules = schedules;
  }

  @Override
  public Snapshot snapshot(Actor actor) {
    var owned = new LinkedHashMap<ActorTaskReference, Task>();
    for (var reference : tasks.list(actor)) {
      try {
        owned.put(reference, tasks.get(actor, reference));
      } catch (TaskNotFoundException removed) {
        /* Task was removed between list and read. */
      }
    }
    var reports =
        owned.entrySet().stream()
            .filter(entry -> entry.getValue().getAgentFeedback().isPresent())
            .map(
                entry ->
                    new Report(
                        entry.getKey(),
                        entry.getValue().getName(),
                        entry.getValue().getCreatedAt(),
                        entry.getValue().getStatus()))
            .sorted(Comparator.comparing(Report::createdAt).reversed())
            .limit(10)
            .toList();
    try {
      var upcoming =
          schedules.list(actor).stream()
              .filter(schedule -> owned.containsKey(schedule.reference()))
              .map(
                  schedule ->
                      new Upcoming(
                          schedule.scheduleId(),
                          schedule.reference(),
                          owned.get(schedule.reference()).getName(),
                          schedule.scheduledAt()))
              .toList();
      return new Snapshot(reports, upcoming, false);
    } catch (TaskScheduleUnavailableException unavailable) {
      return new Snapshot(reports, java.util.List.of(), true);
    }
  }

  @Override
  public String report(Actor actor, ActorTaskReference reference) {
    return tasks
        .get(actor, reference)
        .getAgentFeedback()
        .orElseThrow(() -> new TaskNotFoundException(reference.value()));
  }
}
