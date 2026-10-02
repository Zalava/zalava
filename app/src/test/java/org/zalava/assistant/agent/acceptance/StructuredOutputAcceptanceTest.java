package org.zalava.assistant.agent.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.FileSystemResource;
import org.zalava.assistant.agent.adapter.out.filesystem.FileSystemAgentRunRecorder;
import org.zalava.assistant.agent.adapter.out.policy.ModelBoundaryAgentModel;
import org.zalava.assistant.agent.adapter.out.springai.SpringAiAgentModel;
import org.zalava.assistant.agent.application.DefaultAgentExecution;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.out.AgentContextAssembler;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.assistant.agent.application.port.out.AgentToolSelector;
import org.zalava.assistant.agent.application.port.out.StructuredOutputSchemaException;
import org.zalava.assistant.agent.domain.AgentContext;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.agent.domain.AgentToolSelection;
import reactor.core.publisher.Flux;

/**
 * Real-composition scripted acceptance for the merged CTX-STRUCT-01 structured-output path.
 *
 * <p>Only the provider {@link ChatModel} is scripted. SEA's real {@link SpringAiAgentModel}, {@link
 * ModelBoundaryAgentModel}, {@link DefaultAgentExecution} and {@link FileSystemAgentRunRecorder}
 * run unchanged, together with Spring AI's real {@code StructuredOutputValidationAdvisor} and
 * {@code ChatModelCallAdvisor}. This drives the schema validation, the two retry attempts, attempt
 * counting and run evidence through actual advisor composition rather than a mocked advisor
 * configuration. It is excluded from {@code :app:check} and runs through {@code
 * structuredOutputAcceptanceTest}.
 */
@Tag("structured-output-acceptance")
class StructuredOutputAcceptanceTest {

  private static final String SECRET = "SECRET-STRUCT-ACCEPT-7f3a";
  private static final int BOUNDARY_LIMIT = 16_000;

  @TempDir Path workspace;

  record Answer(String summary, int score) {}

  @Test
  void nativePathSendsProviderSchemaAndConvertsTheResponse() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(StructuredOutputChatOptions.builder().build())
            .reply("{\"summary\":\"ok\",\"score\":7}");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    Answer answer = harness.execution().prompt("native", "summarize", Answer.class);

    assertThat(answer).isEqualTo(new Answer("ok", 7));
    assertThat(model.outputSchema()).as("native path must provide the result schema").isNotBlank();
    assertThat(model.lastUserMessage())
        .as("native providers must not receive portable format instructions")
        .doesNotContain(new BeanOutputConverter<>(Answer.class).getFormat());
    AgentRun run = harness.lastRun("native");
    assertThat(run.status()).isEqualTo(AgentRun.Status.SUCCEEDED);
    assertThat(run.structuredAttemptCount()).isEqualTo(1);
    assertThat(run.structuredFailureCategory()).isEqualTo(AgentRun.StructuredFailureCategory.NONE);
    System.out.printf(
        "STRUCT-ACCEPT native schema=%s attempts=%d status=%s%n",
        model.outputSchema().length(), run.structuredAttemptCount(), run.status());
  }

  @Test
  void portablePathAppendsFormatInstructionsAndConvertsContent() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("{\"summary\":\"portable\",\"score\":3}");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    Answer answer = harness.execution().prompt("portable", "summarize", Answer.class);

    assertThat(answer).isEqualTo(new Answer("portable", 3));
    assertThat(model.outputSchema()).as("portable path must not request native schema").isNull();
    assertThat(model.lastUserMessage())
        .as("portable providers must receive portable format instructions")
        .contains(new BeanOutputConverter<>(Answer.class).getFormat());
    assertThat(harness.lastRun("portable").structuredAttemptCount()).isEqualTo(1);
    System.out.println("STRUCT-ACCEPT portable format-instructions=true attempts=1");
  }

  @Test
  void unsupportedProviderFallsBackToPortableConversion() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("{\"summary\":\"fallback\",\"score\":11}");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    Answer answer = harness.execution().prompt("fallback", "summarize", Answer.class);

    assertThat(answer).isEqualTo(new Answer("fallback", 11));
    assertThat(model.outputSchema())
        .as("a provider without StructuredOutputChatOptions must not be asked for a native schema")
        .isNull();
    assertThat(model.lastUserMessage())
        .as("the unsupported-provider fallback must request portable output")
        .contains(new BeanOutputConverter<>(Answer.class).getFormat());
    assertThat(harness.lastRun("fallback").structuredAttemptCount()).isEqualTo(1);
    System.out.println(
        "STRUCT-ACCEPT unsupported-provider native-schema=absent fallback=portable attempts=1");
  }

  @Test
  void malformedThenValidRetriesOnceAndSucceeds() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("not json at all")
            .reply("{\"summary\":\"recovered\",\"score\":5}");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    Answer answer = harness.execution().prompt("recover", "summarize", Answer.class);

    assertThat(answer).isEqualTo(new Answer("recovered", 5));
    assertThat(model.calls()).isEqualTo(2);
    AgentRun run = harness.lastRun("recover");
    assertThat(run.status()).isEqualTo(AgentRun.Status.SUCCEEDED);
    assertThat(run.structuredAttemptCount()).isEqualTo(2);
    System.out.printf(
        "STRUCT-ACCEPT malformed-then-valid calls=%d attempts=%d%n",
        model.calls(), run.structuredAttemptCount());
  }

  @Test
  void exhaustionStopsAfterTwoRetriesForNativeAndPortable() throws IOException {
    ScriptedChatModel nativeModel =
        new ScriptedChatModel(StructuredOutputChatOptions.builder().build())
            .reply("{\"summary\":\"missing score\"}");
    Harness nativeHarness = harness(nativeModel, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    assertThatThrownBy(
            () -> nativeHarness.execution().prompt("native-exhaust", "summarize", Answer.class))
        .isInstanceOf(StructuredOutputSchemaException.class)
        .hasMessageContaining("after 3 attempts");

    assertThat(nativeModel.calls()).as("one attempt plus two retries").isEqualTo(3);
    AgentRun nativeRun = nativeHarness.lastRun("native-exhaust");
    assertThat(nativeRun.status()).isEqualTo(AgentRun.Status.FAILED);
    assertThat(nativeRun.structuredAttemptCount()).isEqualTo(3);
    assertThat(nativeRun.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION);
    assertThat(nativeRun.errorPreview())
        .as("exhaustion evidence must be sanitized")
        .contains("StructuredOutputSchemaException")
        .doesNotContain("missing score");

    ScriptedChatModel portableModel =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("{\"summary\":\"missing score\"}");
    Harness portableHarness = harness(portableModel, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    assertThatThrownBy(
            () -> portableHarness.execution().prompt("portable-exhaust", "summarize", Answer.class))
        .isInstanceOf(StructuredOutputSchemaException.class)
        .hasMessageContaining("after 3 attempts");

    assertThat(portableModel.calls()).as("one attempt plus two retries").isEqualTo(3);
    AgentRun portableRun = portableHarness.lastRun("portable-exhaust");
    assertThat(portableRun.structuredAttemptCount())
        .as("attempt evidence must survive advisor execution on the model executor")
        .isEqualTo(3);
    assertThat(portableRun.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION);
    System.out.printf(
        "STRUCT-ACCEPT exhaustion native-calls=%d native-attempts=%d portable-calls=%d"
            + " portable-attempts=%d category=%s%n",
        nativeModel.calls(),
        nativeRun.structuredAttemptCount(),
        portableModel.calls(),
        portableRun.structuredAttemptCount(),
        portableRun.structuredFailureCategory());
  }

  @Test
  void schemaMismatchIsClassifiedAsSchemaValidation() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("{\"unexpected\":true}");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    assertThatThrownBy(() -> harness.execution().prompt("mismatch", "summarize", Answer.class))
        .isInstanceOf(StructuredOutputSchemaException.class);

    AgentRun run = harness.lastRun("mismatch");
    assertThat(run.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION);
    System.out.println("STRUCT-ACCEPT schema-mismatch category=SCHEMA_VALIDATION");
  }

  @Test
  void sanitizedErrorsRedactConfiguredSecretsFromRunEvidence() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .fail(new IllegalStateException("provider failed with " + SECRET));
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    assertThatThrownBy(() -> harness.execution().prompt("secret", "summarize", Answer.class))
        .isInstanceOf(IllegalStateException.class);

    AgentRun run = harness.lastRun("secret");
    assertThat(run.status()).isEqualTo(AgentRun.Status.FAILED);
    assertThat(run.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.MODEL_FAILURE);
    assertThat(run.errorPreview())
        .as("configured secrets must never appear in structured run evidence")
        .doesNotContain(SECRET)
        .contains(ModelBoundary.REDACTION);
    System.out.printf(
        "STRUCT-ACCEPT sanitized-errors secret-leaks=0 category=%s%n",
        run.structuredFailureCategory());
  }

  @Test
  void runEvidenceIsRestartSafe() throws IOException {
    Harness harness =
        harness(
            new ScriptedChatModel(ToolCallingChatOptions.builder().build())
                .reply("{\"summary\":\"persisted\",\"score\":9}"),
            new ModelBoundary(BOUNDARY_LIMIT, SECRET));
    harness.execution().prompt("restart-ok", "summarize", Answer.class);

    Harness failure =
        harness(
            new ScriptedChatModel(ToolCallingChatOptions.builder().build())
                .reply("{\"summary\":\"missing\"}"),
            new ModelBoundary(BOUNDARY_LIMIT, SECRET));
    assertThatThrownBy(() -> failure.execution().prompt("restart-bad", "summarize", Answer.class))
        .isInstanceOf(StructuredOutputSchemaException.class);

    FileSystemAgentRunRecorder reloaded =
        new FileSystemAgentRunRecorder(new FileSystemResource(workspace));
    AgentRun success =
        reloaded.recent().stream()
            .filter(run -> run.conversationId().equals("restart-ok"))
            .findFirst()
            .orElseThrow();
    AgentRun exhausted =
        reloaded.recent().stream()
            .filter(run -> run.conversationId().equals("restart-bad"))
            .findFirst()
            .orElseThrow();
    assertThat(success.structuredAttemptCount()).isEqualTo(1);
    assertThat(success.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.NONE);
    assertThat(exhausted.structuredAttemptCount()).isEqualTo(3);
    assertThat(exhausted.structuredFailureCategory())
        .isEqualTo(AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION);
    System.out.printf(
        "STRUCT-ACCEPT restart success-attempts=%d exhausted-attempts=%d records=%d%n",
        success.structuredAttemptCount(),
        exhausted.structuredAttemptCount(),
        reloaded.recent().size());
  }

  @Test
  void conversationalTurnsRemainUnaffected() throws IOException {
    ScriptedChatModel model =
        new ScriptedChatModel(ToolCallingChatOptions.builder().build())
            .reply("scripted-conversational-reply");
    Harness harness = harness(model, new ModelBoundary(BOUNDARY_LIMIT, SECRET));

    String reply = harness.execution().respondTo("conversation", "hello");

    assertThat(reply).isEqualTo("scripted-conversational-reply");
    assertThat(model.outputSchema()).isNull();
    assertThat(model.lastUserMessage())
        .as("conversational turns must not receive structured format instructions")
        .isEqualTo("hello");
    AgentRun run = harness.lastRun("conversation");
    assertThat(run.promptType()).isEqualTo(AgentRun.PromptType.CONVERSATIONAL);
    assertThat(run.structuredAttemptCount()).isZero();
    assertThat(run.structuredFailureCategory()).isEqualTo(AgentRun.StructuredFailureCategory.NONE);
    System.out.println("STRUCT-ACCEPT conversational structured-evidence=absent");
  }

  private Harness harness(ScriptedChatModel chatModel, ModelBoundary boundary) {
    FileSystemAgentRunRecorder recorder;
    try {
      recorder = new FileSystemAgentRunRecorder(new FileSystemResource(workspace));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
    AgentModel model =
        new ModelBoundaryAgentModel(
            new SpringAiAgentModel(ChatClient.builder(chatModel).build()), boundary);
    AgentToolSelector selector =
        (conversationId, input) -> new AgentToolSelection(List.of(), List.of(), List.of());
    AgentContextAssembler assembler =
        (input, selection) ->
            new AgentContext(
                input,
                1,
                BOUNDARY_LIMIT,
                input.length(),
                List.of(
                    new AgentContext.SourceMetric("user_prompt", input.length(), input.length())));
    DefaultAgentExecution execution =
        new DefaultAgentExecution(
            model,
            selector,
            assembler,
            recorder,
            Instant::now,
            () -> UUID.randomUUID().toString(),
            boundary);
    return new Harness(execution, recorder);
  }

  private record Harness(DefaultAgentExecution execution, FileSystemAgentRunRecorder recorder) {
    AgentRun lastRun(String conversationId) {
      return recorder.recent().stream()
          .filter(run -> run.conversationId().equals(conversationId))
          .reduce((first, second) -> second)
          .orElseThrow();
    }
  }

  private static final class ScriptedChatModel implements ChatModel {
    private final ChatOptions options;
    private final Deque<Function<Prompt, ChatResponse>> script = new ArrayDeque<>();
    private final AtomicReference<String> outputSchema = new AtomicReference<>();
    private final AtomicReference<String> lastUserMessage = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    private ScriptedChatModel(ChatOptions options) {
      this.options = options;
    }

    ScriptedChatModel reply(String content) {
      script.add(prompt -> text(content));
      return this;
    }

    ScriptedChatModel fail(RuntimeException failure) {
      script.add(
          prompt -> {
            throw failure;
          });
      return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      calls.incrementAndGet();
      lastUserMessage.set(prompt.getLastUserOrToolResponseMessage().getText());
      if (prompt.getOptions() instanceof StructuredOutputChatOptions structured
          && structured.getOutputSchema() != null) {
        outputSchema.set(structured.getOutputSchema());
      }
      Function<Prompt, ChatResponse> next = script.size() > 1 ? script.poll() : script.peek();
      if (next == null) {
        throw new IllegalStateException("ScriptedChatModel has no response configured");
      }
      return next.apply(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getOptions() {
      return options;
    }

    int calls() {
      return calls.get();
    }

    String outputSchema() {
      return outputSchema.get();
    }

    String lastUserMessage() {
      return lastUserMessage.get();
    }

    private static ChatResponse text(String content) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
  }
}
