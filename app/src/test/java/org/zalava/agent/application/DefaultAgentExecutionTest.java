package org.zalava.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.zalava.agent.ConversationChannelContext;
import org.zalava.agent.application.port.out.AgentClock;
import org.zalava.agent.application.port.out.AgentContextAssembler;
import org.zalava.agent.application.port.out.AgentModel;
import org.zalava.agent.application.port.out.AgentRunIdGenerator;
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.agent.application.port.out.AgentToolSelector;
import org.zalava.agent.application.port.out.StructuredOutputSchemaException;
import org.zalava.agent.application.port.out.StructuredRunEvidence;
import org.zalava.agent.domain.AgentContext;
import org.zalava.agent.domain.AgentRun;
import org.zalava.agent.domain.AgentToolSelection;
import org.junit.jupiter.api.Test;

class DefaultAgentExecutionTest {

  @Test
  void recordsContextAndSelectedToolsForSuccessfulExecution() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution = execution(runs, (conversationId, prompt, tools) -> "answer");

    assertThat(execution.respondTo("conversation-1", "question")).isEqualTo("answer");

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.id()).isEqualTo("run-1");
              assertThat(run.promptType()).isEqualTo(AgentRun.PromptType.CONVERSATIONAL);
              assertThat(run.selectedToolCount()).isEqualTo(1);
              assertThat(run.contextSourceMetrics())
                  .singleElement()
                  .extracting(AgentRun.ContextSourceMetric::sourceType)
                  .isEqualTo("user_prompt");
              assertThat(run.status()).isEqualTo(AgentRun.Status.SUCCEEDED);
              assertThat(run.resultPreview()).isEqualTo("answer");
            });
  }

  @Test
  void recordsFailureBeforeRethrowingModelError() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution =
        execution(
            runs,
            (conversationId, prompt, tools) -> {
              throw new IllegalStateException("model failed");
            });

    assertThatThrownBy(() -> execution.respondTo("conversation-1", "question"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model failed");

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.status()).isEqualTo(AgentRun.Status.FAILED);
              assertThat(run.errorPreview()).isEqualTo("IllegalStateException: model failed");
            });
  }

  @Test
  void bindsTrustedTelegramChannelForTheModelToolInvocationWindow() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution =
        execution(
            runs,
            (conversationId, prompt, tools) -> {
              assertThat(ConversationChannelContext.current()).contains("telegram");
              return "answer";
            });

    assertThat(execution.respondTo("telegram-123", "question")).isEqualTo("answer");
    assertThat(ConversationChannelContext.current()).isEmpty();
  }

  @Test
  void redactsSecretsFromPersistedInputResultAndFailurePreviews() {
    List<AgentRun> runs = new ArrayList<>();
    ModelBoundary boundary = new ModelBoundary(100, "top-secret");
    DefaultAgentExecution execution =
        execution(
            runs,
            (conversationId, prompt, tools) -> {
              assertThat(prompt).isEqualTo("question top-secret");
              return "answer top-secret";
            },
            boundary);

    execution.respondTo("conversation-1", "question top-secret");

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.promptPreview()).isEqualTo("question [REDACTED]");
              assertThat(run.resultPreview()).isEqualTo("answer [REDACTED]");
            });
  }

  @Test
  void classifiesStructuredSchemaExhaustionAsSchemaValidation() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution =
        execution(
            runs,
            new AgentModel() {
              @Override
              public String conversational(
                  String conversationId, String prompt, List<Object> tools) {
                return "answer";
              }

              @Override
              public <T> T structured(
                  String conversationId, String prompt, List<Object> tools, Class<T> resultType) {
                throw new StructuredOutputSchemaException(
                    "Structured output schema validation failed after 3 attempts",
                    new IllegalStateException("invalid json"));
              }
            });

    assertThatThrownBy(() -> execution.prompt("conversation-1", "question", String.class))
        .isInstanceOf(StructuredOutputSchemaException.class);

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.promptType()).isEqualTo(AgentRun.PromptType.STRUCTURED);
              assertThat(run.status()).isEqualTo(AgentRun.Status.FAILED);
              assertThat(run.structuredFailureCategory())
                  .isEqualTo(AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION);
            });
  }

  @Test
  void recordsStructuredAttemptCountFromModelEvidence() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution =
        execution(
            runs,
            new AgentModel() {
              @Override
              public String conversational(
                  String conversationId, String prompt, List<Object> tools) {
                return "answer";
              }

              @Override
              @SuppressWarnings("unchecked")
              public <T> T structured(
                  String conversationId, String prompt, List<Object> tools, Class<T> resultType) {
                AtomicInteger attempts = StructuredRunEvidence.begin();
                attempts.addAndGet(3);
                return (T) "done";
              }
            });

    assertThat(execution.prompt("conversation-1", "question", String.class)).isEqualTo("done");

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.promptType()).isEqualTo(AgentRun.PromptType.STRUCTURED);
              assertThat(run.structuredAttemptCount()).isEqualTo(3);
              assertThat(run.structuredFailureCategory())
                  .isEqualTo(AgentRun.StructuredFailureCategory.NONE);
            });
  }

  @Test
  void conversationalRunsCarryNoStructuredEvidence() {
    List<AgentRun> runs = new ArrayList<>();
    DefaultAgentExecution execution = execution(runs, (conversationId, prompt, tools) -> "answer");

    execution.respondTo("conversation-1", "question");

    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.promptType()).isEqualTo(AgentRun.PromptType.CONVERSATIONAL);
              assertThat(run.structuredAttemptCount()).isZero();
              assertThat(run.structuredFailureCategory())
                  .isEqualTo(AgentRun.StructuredFailureCategory.NONE);
            });
  }

  private static DefaultAgentExecution execution(List<AgentRun> runs, ConversationalResult result) {
    return execution(runs, result, null);
  }

  private static DefaultAgentExecution execution(List<AgentRun> runs, AgentModel model) {
    return execution(runs, model, null);
  }

  private static DefaultAgentExecution execution(
      List<AgentRun> runs, ConversationalResult result, ModelBoundary boundary) {
    return execution(
        runs,
        new AgentModel() {
          @Override
          public String conversational(String conversationId, String prompt, List<Object> tools) {
            return result.respond(conversationId, prompt, tools);
          }

          @Override
          public <T> T structured(
              String conversationId, String prompt, List<Object> tools, Class<T> resultType) {
            throw new UnsupportedOperationException();
          }
        },
        boundary);
  }

  private static DefaultAgentExecution execution(
      List<AgentRun> runs, AgentModel model, ModelBoundary boundary) {
    AgentToolSelector selector =
        (conversationId, input) ->
            new AgentToolSelection(List.of(new Object()), List.of(), List.of());
    AgentContextAssembler assembler =
        (input, selection) ->
            new AgentContext(
                input,
                1,
                100,
                input.length(),
                List.of(
                    new AgentContext.SourceMetric("user_prompt", input.length(), input.length())));
    AgentRunStore store =
        new AgentRunStore() {
          @Override
          public void record(AgentRun run) {
            runs.add(run);
          }

          @Override
          public List<AgentRun> recent() {
            return List.copyOf(runs);
          }
        };
    AgentClock clock = () -> Instant.parse("2026-08-06T10:00:00Z");
    AgentRunIdGenerator ids = () -> "run-1";
    return new DefaultAgentExecution(model, selector, assembler, store, clock, ids, boundary);
  }

  @FunctionalInterface
  private interface ConversationalResult {
    String respond(String conversationId, String prompt, List<Object> tools);
  }
}
