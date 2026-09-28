package org.zalava.tasks.adapter.in.jobrunr;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zalava.tasks.application.port.in.TaskCommands;

@Component
public class RecurringTaskHandler {

  private static final Logger LOGGER =
      new JobRunrDashboardLogger(LoggerFactory.getLogger(RecurringTaskHandler.class));

  private final TaskCommands taskCommands;

  public RecurringTaskHandler(TaskCommands taskCommands) {
    this.taskCommands = taskCommands;
  }

  @Job(name = "Recurring task '%0'", retries = 3)
  public void executeTask(String recurringTaskId) {
    taskCommands.createTaskFromRecurringTask(recurringTaskId);
  }
}
