package org.zalava.assistant.agent.application.port.in;

import org.zalava.tasks.application.port.out.TaskAgent;

public interface AgentExecution {
  String respondTo(String conversationId, String question);

  /**
   * Streaming variant of {@link #respondTo}. Boundary-safe deltas are forwarded to the listener as
   * they are produced; {@link AgentStreamListener#onComplete} carries the full text. Returns the
   * complete response text after the stream finishes. The default delegates to the blocking variant
   * so existing callers and decorators are untouched.
   */
  default String respondTo(String conversationId, String question, AgentStreamListener listener) {
    try {
      String response = respondTo(conversationId, question);
      listener.onDelta(response);
      listener.onComplete(response);
      return response;
    } catch (RuntimeException failure) {
      listener.onError(failure);
      throw failure;
    }
  }

  <T> T prompt(String conversationId, String input, Class<T> resultType);

  default TaskAgent.Result task(String conversationId, String input) {
    return prompt(conversationId, input, TaskAgent.Result.class);
  }
}
