package org.zalava.assistant.agent.adapter.out.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.adapter.out.springai.SpringAiAgentModel;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;

/**
 * Covers the streamed redaction in {@link ModelBoundaryAgentModel}: no configured secret may ever
 * appear in the emitted deltas, the emitted prefix must stay monotonic, and the concatenation of
 * deltas plus the final tail equals the fully redacted text.
 */
class ModelBoundaryAgentModelStreamingTest {
  @Test
  void taskDelegatesToManualTaskPathAndRedactsFeedback() {
    SpringAiAgentModel delegate = mock(SpringAiAgentModel.class);
    when(delegate.task(anyString(), anyString(), anyList()))
        .thenReturn(new TaskAgent.Result(Task.Status.completed, "completed with secret-value"));
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate, new ModelBoundary(1000, "secret-value"));

    TaskAgent.Result result = model.task("task-1", "prompt", List.of());

    assertThat(result.newStatus()).isEqualTo(Task.Status.completed);
    assertThat(result.feedback()).doesNotContain("secret-value");
  }

  @Test
  void streamsPlainContentWithoutRedactionAndFlushesTheExactTail() {
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(
            delegate("Hello there! General Kenobi."), new ModelBoundary(1000, ""));

    List<String> deltas = new ArrayList<>();
    String result = model.conversational("c1", "p", List.of(), deltas::add);

    assertThat(result).isEqualTo("Hello there! General Kenobi.");
    assertThat(String.join("", deltas)).isEqualTo("Hello there! General Kenobi.");
  }

  @Test
  void neverEmitsASecretFragmentEvenWhenItStraddlesAChunkBoundary() {
    ModelBoundary boundary = new ModelBoundary(1000, "wxyz");
    // Raw stream splits the secret across chunks: "ab" + "wxyz" + "!...".
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate("ab" + "wxyz" + "! tail"), boundary);

    List<String> deltas = new ArrayList<>();
    String result = model.conversational("c1", "p", List.of(), deltas::add);

    String joined = String.join("", deltas);
    assertThat(joined).doesNotContain("wxyz");
    // The straddling occurrence is fully redacted in the emitted prefix...
    assertThat(joined).contains("[REDACTED]");
    // ...and the final flush completes the redacted text exactly.
    assertThat(joined + suffixOf(result, joined)).isEqualTo("ab[REDACTED]! tail");
    assertThat(result).isEqualTo("ab[REDACTED]! tail");
  }

  @Test
  void holdsBackLongerSecretsBeyondChunkBoundaries() {
    ModelBoundary boundary = new ModelBoundary(1000, "super-secret-value-42");
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate("before super-secret-value-42 after"), boundary);

    List<String> deltas = new ArrayList<>();
    model.conversational("c1", "p", List.of(), deltas::add);

    String joined = String.join("", deltas);
    assertThat(joined).doesNotContain("super-secret-value-42");
    assertThat(joined).contains("[REDACTED]");
    assertThat(joined).startsWith("before ");
  }

  @Test
  void flushesTheRemainingRedactedTailOnCompletion() {
    // Hold-back means the last (L-1) raw characters are never emitted before completion.
    ModelBoundary boundary = new ModelBoundary(1000, "needle");
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate("tail-end needle"), boundary);

    List<String> deltas = new ArrayList<>();
    model.conversational("c1", "p", List.of(), deltas::add);

    String joined = String.join("", deltas);
    assertThat(joined).doesNotContain("needle");
    assertThat(joined).endsWith("tail-end [REDACTED]");
  }

  @Test
  void streamsASequenceOfSmallChunksAndRedactsEveryVisibleOccurrence() {
    ModelBoundary boundary = new ModelBoundary(1000, "xy");
    StringBuilder raw = new StringBuilder();
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < 26; i++) {
      String piece = "chunk" + i + (i % 3 == 0 ? "-xy-" : "-");
      chunks.add(piece);
      raw.append(piece);
    }
    ModelBoundaryAgentModel model = new ModelBoundaryAgentModel(delegate(raw.toString()), boundary);

    List<String> deltas = new ArrayList<>();
    model.conversational("c1", "p", List.of(), deltas::add);

    String joined = String.join("", deltas);
    assertThat(joined).doesNotContain("xy");
    assertThat(joined).contains("[REDACTED]");
    assertThat(joined).isEqualTo(boundary.redact(raw.toString()));
  }

  @Test
  void noSecretsConfiguredStreamsTheRawChunksUnchanged() {
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(
            delegate(new String[] {"part1 ", "part2 ", "part3"}), new ModelBoundary(1000, ""));

    List<String> deltas = new ArrayList<>();
    model.conversational("c1", "p", List.of(), deltas::add);

    assertThat(String.join("", deltas)).isEqualTo("part1 part2 part3");
  }

  @Test
  void anEmptyOrBlankChunkIsIgnoredWithoutBreakingMonotonicity() {
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(
            delegate(new String[] {"a", "", "b"}), new ModelBoundary(1000, ""));

    List<String> deltas = new ArrayList<>();
    model.conversational("c1", "p", List.of(), deltas::add);

    assertThat(String.join("", deltas)).isEqualTo("ab");
  }

  @Test
  void aNonMonotonicRedactionRefusesToEmitRatherThanLeaking() {
    // Without hold-back, "ab" is emitted as-is; a later chunk completes the secret "abc", whose
    // redaction no longer extends the emitted prefix — the adapter must fail closed.
    ModelBoundary completing =
        boundaryWithRedaction(0, content -> content.equals("abc") ? "[REDACTED]" : content);
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate(new String[] {"ab", "c"}), completing);

    List<String> deltas = new ArrayList<>();
    assertThatThrownBy(() -> model.conversational("c1", "p", List.of(), deltas::add))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monotonic");
  }

  @Test
  void aDivergingFinalRedactionRefusesToFlushAnInconsistentTail() {
    // With hold-back 2 the streamed prefix only ever covers "a"; the final full text "abc"
    // redacts to something that does not extend it — the adapter must refuse to flush.
    ModelBoundary diverging =
        boundaryWithRedaction(3, content -> content.equals("abc") ? "zzz" : content);
    ModelBoundaryAgentModel model =
        new ModelBoundaryAgentModel(delegate(new String[] {"a", "b", "c"}), diverging);

    List<String> deltas = new ArrayList<>();
    assertThatThrownBy(() -> model.conversational("c1", "p", List.of(), deltas::add))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diverges");
  }

  @Test
  void streamingAndBlockingPathsReturnIdenticalFullText() {
    ModelBoundary boundary = new ModelBoundary(1000, "secret-1,another-secret");
    String text = "a secret-1 and an another-secret end";
    ModelBoundaryAgentModel model = new ModelBoundaryAgentModel(delegate(text), boundary);

    List<String> deltas = new ArrayList<>();
    String streamed = model.conversational("c1", "p", List.of(), deltas::add);
    String blocked = model.conversational("c1", "p", List.of());

    assertThat(streamed).isEqualTo(boundary.redact(text));
    assertThat(blocked).isEqualTo(streamed);
    assertThat(String.join("", deltas)).isEqualTo(streamed);
  }

  /**
   * A boundary whose {@code redact} is the given function; identity input, no secrets, and the
   * given reported maximum secret length (drives the streaming hold-back window).
   */
  private static ModelBoundary boundaryWithRedaction(
      int maximumSecretLength, java.util.function.UnaryOperator<String> redaction) {
    ModelBoundary boundary = mock(ModelBoundary.class);
    when(boundary.redact(anyString()))
        .thenAnswer(invocation -> redaction.apply(invocation.getArgument(0)));
    when(boundary.input(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    when(boundary.maximumSecretLength()).thenReturn(maximumSecretLength);
    when(boundary.secrets()).thenReturn(List.of());
    return boundary;
  }

  /** Streams the given chunks through the streaming AgentModel contract. */
  private static SpringAiAgentModel delegate(String... chunks) {
    SpringAiAgentModel model = mock(SpringAiAgentModel.class);
    when(model.conversational(anyString(), anyString(), anyList(), any()))
        .thenAnswer(
            invocation -> {
              Consumer<String> deltas = invocation.getArgument(3);
              for (String chunk : chunks) {
                deltas.accept(chunk);
              }
              return String.join("", chunks);
            });
    when(model.conversational(anyString(), anyString(), anyList()))
        .thenReturn(String.join("", chunks));
    return model;
  }

  private static String suffixOf(String full, String prefix) {
    return full.startsWith(prefix) ? full.substring(prefix.length()) : "<diverged>";
  }
}
