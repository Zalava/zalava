package org.zalava.chat.domain;

import java.util.List;
import org.zalava.tasks.domain.TaskReference;

public record ChatTurn(String text, List<TaskReference> taskReferences) {
  public ChatTurn {
    taskReferences = List.copyOf(taskReferences);
  }
}
