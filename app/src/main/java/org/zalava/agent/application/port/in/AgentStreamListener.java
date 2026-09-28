package org.zalava.agent.application.port.in;

/**
 * Receives progressive output of a streamed agent turn. Callbacks run on the subscribing thread.
 */
public interface AgentStreamListener {
  /** An incremental, boundary-safe text batch of the assistant response. */
  void onDelta(String text);

  /** The turn finished; the complete response text. */
  void onComplete(String fullText);

  /** The turn failed; no completion callback follows. */
  void onError(RuntimeException failure);
}
