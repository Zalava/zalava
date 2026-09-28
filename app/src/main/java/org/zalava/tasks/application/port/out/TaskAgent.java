package org.zalava.tasks.application.port.out;

import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

public interface TaskAgent {

  Result execute(String taskId, TaskReference taskReference, String prompt);

  record Result(Task.Status newStatus, String feedback) {}
}
