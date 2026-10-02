package org.zalava.assistant.chat.adapter.out.agent;

import org.zalava.assistant.agent.application.port.in.AgentExecution;
import org.zalava.assistant.chat.application.port.out.ChatAgent;
import org.zalava.assistant.chat.domain.ChatTurn;
import org.zalava.tasks.capture.TaskCreationContext;

public final class TaskCapturingChatAgent implements ChatAgent {
  private final AgentExecution agent;
  private final TaskCreationContext taskCreationContext;

  public TaskCapturingChatAgent(AgentExecution agent, TaskCreationContext taskCreationContext) {
    this.agent = agent;
    this.taskCreationContext = taskCreationContext;
  }

  @Override
  public ChatTurn respondTo(String conversationId, String message) {
    TaskCreationContext.Capture<String> capture =
        taskCreationContext.capture(() -> agent.respondTo(conversationId, message));
    return new ChatTurn(capture.value(), capture.taskReferences());
  }
}
