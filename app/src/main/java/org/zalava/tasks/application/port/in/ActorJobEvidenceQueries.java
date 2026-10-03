package org.zalava.tasks.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

public interface ActorJobEvidenceQueries {
  Snapshot snapshot(Actor actor);

  String report(Actor actor, ActorTaskReference reference);

  record Snapshot(List<Report> reports, List<Upcoming> upcoming, boolean schedulingUnavailable) {
    public Snapshot {
      reports = List.copyOf(reports);
      upcoming = List.copyOf(upcoming);
    }

    public static Snapshot empty() {
      return new Snapshot(List.of(), List.of(), false);
    }
  }

  record Report(ActorTaskReference reference, String name, Instant createdAt, Task.Status status) {}

  record Upcoming(
      UUID scheduleId, ActorTaskReference reference, String name, Instant scheduledAt) {}
}
