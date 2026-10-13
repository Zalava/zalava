package org.zalava.assistant.agent.application.port.out;

import java.util.List;
import java.util.function.Consumer;
import org.zalava.tasks.application.port.out.TaskAgent;

public interface AgentModel {
  String conversational(String conversationId, String prompt, List<Object> tools);

  /**
   * Streaming variant of {@link #conversational}. Raw model deltas are forwarded to {@code deltas}
   * as they arrive; the returned value is the complete concatenated response. The default delegates
   * to the blocking variant so existing adapters keep working.
   */
  default String conversational(
      String conversationId, String prompt, List<Object> tools, Consumer<String> deltas) {
    String content = conversational(conversationId, prompt, tools);
    if (content != null && !content.isEmpty()) deltas.accept(content);
    return content;
  }

  default String conversationalInput(
      String conversationId, AgentModelInput input, List<Object> tools) {
    return conversational(conversationId, input.modelText(), tools);
  }

  default String conversationalInput(
      String conversationId, AgentModelInput input, List<Object> tools, Consumer<String> deltas) {
    return conversational(conversationId, input.modelText(), tools, deltas);
  }

  <T> T structured(String conversationId, String prompt, List<Object> tools, Class<T> resultType);

  /** One task-loop model turn. Implementations must not auto-execute requested tools. */
  default TaskAgent.Result task(String conversationId, String prompt, List<Object> tools) {
    return structured(conversationId, prompt, tools, TaskAgent.Result.class);
  }
}
