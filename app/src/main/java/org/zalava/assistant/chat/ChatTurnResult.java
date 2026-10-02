package org.zalava.assistant.chat;

import java.util.List;
import org.zalava.tasks.domain.TaskReference;

public record ChatTurnResult(String text, List<TaskReference> jobReferences) {

  public ChatTurnResult {
    jobReferences = List.copyOf(jobReferences);
  }
}
