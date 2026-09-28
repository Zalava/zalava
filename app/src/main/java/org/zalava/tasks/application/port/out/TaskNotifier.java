package org.zalava.tasks.application.port.out;

import org.zalava.tasks.domain.Task;

public interface TaskNotifier {

  void notify(String taskName, Task.Status status, String feedback);
}
