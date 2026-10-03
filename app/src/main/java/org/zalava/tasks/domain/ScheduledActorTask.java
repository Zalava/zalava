package org.zalava.tasks.domain;

import java.time.Instant;
import java.util.UUID;

public record ScheduledActorTask(
    UUID scheduleId, ActorTaskReference reference, Instant scheduledAt) {}
