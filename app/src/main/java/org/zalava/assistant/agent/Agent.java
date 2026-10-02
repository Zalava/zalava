package org.zalava.assistant.agent;

import org.zalava.tasks.application.port.out.TaskAgent;

public interface Agent {

  String respondTo(String conversationId, String question);

  <T> T prompt(String conversationId, String input, Class<T> result);

  default TaskAgent.Result task(String conversationId, String input) {
    return prompt(conversationId, input, TaskAgent.Result.class);
  }
}
