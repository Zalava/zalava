package org.zalava.tasks.domain;

public final class TaskScheduleUnavailableException extends RuntimeException {
  public TaskScheduleUnavailableException(Throwable cause) {
    super("Scheduled work is unavailable", cause);
  }
}
