package org.zalava.assistant.channels.adapter.out.tasks;

import org.zalava.assistant.channels.application.DefaultChannelApprovalCommands;
import org.zalava.assistant.channels.application.port.out.ChannelTasks;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.zalava.tasks.domain.TaskReference;

public final class TaskChannelAdapter implements ChannelTasks {
  private final TaskCommands commands;
  private final TaskQueries queries;

  public TaskChannelAdapter(TaskCommands commands, TaskQueries queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @Override
  public boolean isAwaitingHumanInput(String taskReference) {
    try {
      return queries.getTask(reference(taskReference)).getStatus()
          == Task.Status.awaiting_human_input;
    } catch (TaskNotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(taskReference);
    }
  }

  @Override
  public void resume(String taskReference) {
    commands.resume(reference(taskReference));
  }

  private static TaskReference reference(String value) {
    String[] parts = value.split("/", 2);
    if (parts.length != 2) throw new IllegalArgumentException("Invalid task reference");
    return TaskReference.parse(parts[0], parts[1]);
  }
}
