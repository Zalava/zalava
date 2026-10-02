package org.zalava.assistant.agent.adapter.out.policy;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.zalava.assistant.agent.adapter.out.springai.SpringAiAgentModel;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.tasks.application.port.out.TaskAgent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Primary
public final class ModelBoundaryAgentModel implements AgentModel {
  private final SpringAiAgentModel delegate;
  private final ModelBoundary boundary;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public ModelBoundaryAgentModel(SpringAiAgentModel delegate, ModelBoundary boundary) {
    this.delegate = delegate;
    this.boundary = boundary;
  }

  @Override
  public String conversational(String conversationId, String prompt, List<Object> tools) {
    return boundary.redact(delegate.conversational(conversationId, boundary.input(prompt), tools));
  }

  /**
   * Streams boundary-safe deltas without ever emitting a fragment of a configured secret.
   *
   * <p>Correctness argument. Let L be the longest configured secret length and let the emitted
   * state be a raw boundary {@code k} with {@code redact(raw[0,k)) == emitted}. A boundary is safe
   * when no occurrence of a configured secret in the final text straddles it; for safe boundaries
   * {@code redact(raw[0,k))} is a prefix of the final redacted text. Two rules make every emitted
   * boundary safe w.r.t. any future extension of the stream: (1) hold-back — the boundary never
   * advances past {@code raw.length() - (L - 1)}, so an occurrence extending beyond the received
   * buffer cannot straddle it (its start would have to lie simultaneously at or before {@code
   * raw.length() - L} and after it); (2) visible-occurrence pull-back — an occurrence fully
   * contained in the received buffer that straddles the candidate boundary pulls the boundary back
   * to the occurrence start. On completion the remaining redacted tail is flushed, so the
   * concatenation of all emitted deltas equals {@code boundary.redact(fullText)}.
   */
  @Override
  public String conversational(
      String conversationId, String prompt, List<Object> tools, Consumer<String> deltas) {
    StreamState state = new StreamState();
    state.holdBack = Math.max(0, boundary.maximumSecretLength() - 1);
    String result =
        delegate.conversational(
            conversationId, boundary.input(prompt), tools, chunk -> onChunk(state, chunk, deltas));
    String full = boundary.redact(result);
    if (!full.startsWith(state.emitted.toString())) {
      throw new IllegalStateException(
          "Final redacted text diverges from emitted prefixes; refusing to emit inconsistent tail");
    }
    String tail = full.substring(state.emitted.length());
    if (!tail.isEmpty()) {
      deltas.accept(tail);
    }
    return full;
  }

  private void onChunk(StreamState state, String chunk, Consumer<String> deltas) {
    if (chunk == null || chunk.isEmpty()) {
      return;
    }
    state.raw.append(chunk);
    int limit = Math.max(0, state.raw.length() - state.holdBack);
    limit = pullBackToVisibleOccurrenceStart(state.raw, limit);
    if (limit > state.rawBoundary) {
      emitUpTo(state, limit, deltas);
    }
  }

  private void emitUpTo(StreamState state, int limit, Consumer<String> deltas) {
    String prefixRedacted = boundary.redact(state.raw.substring(0, limit));
    if (!prefixRedacted.startsWith(state.emitted.toString())) {
      throw new IllegalStateException(
          "Redacted prefix is not monotonic; refusing to emit inconsistent delta");
    }
    String addition = prefixRedacted.substring(state.emitted.length());
    if (!addition.isEmpty()) {
      state.emitted.append(addition);
      deltas.accept(addition);
    }
    state.rawBoundary = limit;
  }

  /**
   * Returns the largest boundary {@code <= limit} that no secret occurrence visible in {@code raw}
   * straddles: an occurrence {@code [s, e)} with {@code s < limit < e} pulls the boundary back to
   * {@code s}. Iterates to a fixpoint because lowering the boundary can expose earlier straddling
   * occurrences.
   */
  private int pullBackToVisibleOccurrenceStart(StringBuilder raw, int limit) {
    if (limit <= 0) {
      return limit;
    }
    boolean changed = true;
    while (changed && limit > 0) {
      changed = false;
      for (String secret : boundary.secrets()) {
        int start = raw.indexOf(secret, Math.max(0, limit - secret.length()));
        while (start >= 0 && start < limit) {
          int end = start + secret.length();
          if (end > limit) {
            limit = start;
            changed = true;
            break;
          }
          start = raw.indexOf(secret, start + 1);
        }
      }
    }
    return limit;
  }

  @Override
  public <T> T structured(
      String conversationId, String prompt, List<Object> tools, Class<T> resultType) {
    T result = delegate.structured(conversationId, boundary.input(prompt), tools, resultType);
    if (result == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(
          boundary.redact(OBJECT_MAPPER.writeValueAsString(result)), resultType);
    } catch (JacksonException failure) {
      throw new IllegalStateException("Unable to apply the model output boundary", failure);
    }
  }

  @Override
  public TaskAgent.Result task(String conversationId, String prompt, List<Object> tools) {
    TaskAgent.Result result = delegate.task(conversationId, boundary.input(prompt), tools);
    if (result == null) {
      return null;
    }
    return new TaskAgent.Result(result.newStatus(), boundary.redact(result.feedback()));
  }

  /** Per-stream emission state; a new instance per call keeps concurrent streams isolated. */
  private static final class StreamState {
    private final StringBuilder raw = new StringBuilder();
    private final StringBuilder emitted = new StringBuilder();
    private int holdBack;
    private int rawBoundary;
  }
}
