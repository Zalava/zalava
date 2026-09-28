package org.zalava.agent.application;

import java.time.Instant;
import org.zalava.agent.application.port.in.AgentExecution;
import org.zalava.agent.application.port.in.AgentStreamListener;
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
import org.zalava.observability.application.port.out.OperationalMetrics;
import org.zalava.tasks.application.port.out.TaskAgent;

public final class DefaultAgentExecution implements AgentExecution {
  private final AgentModel model;
  private final AgentToolSelector toolSelector;
  private final AgentContextAssembler contextAssembler;
  private final AgentRunStore runStore;
  private final AgentClock clock;
  private final AgentRunIdGenerator idGenerator;
  private final ModelBoundary boundary;
  private final OperationalMetrics metrics;

  public DefaultAgentExecution(
      AgentModel model,
      AgentToolSelector toolSelector,
      AgentContextAssembler contextAssembler,
      AgentRunStore runStore,
      AgentClock clock,
      AgentRunIdGenerator idGenerator) {
    this(
        model,
        toolSelector,
        contextAssembler,
        runStore,
        clock,
        idGenerator,
        null,
        OperationalMetrics.NOOP);
  }

  public DefaultAgentExecution(
      AgentModel model,
      AgentToolSelector toolSelector,
      AgentContextAssembler contextAssembler,
      AgentRunStore runStore,
      AgentClock clock,
      AgentRunIdGenerator idGenerator,
      ModelBoundary boundary) {
    this(
        model,
        toolSelector,
        contextAssembler,
        runStore,
        clock,
        idGenerator,
        boundary,
        OperationalMetrics.NOOP);
  }

  public DefaultAgentExecution(
      AgentModel model,
      AgentToolSelector toolSelector,
      AgentContextAssembler contextAssembler,
      AgentRunStore runStore,
      AgentClock clock,
      AgentRunIdGenerator idGenerator,
      ModelBoundary boundary,
      OperationalMetrics metrics) {
    this.model = model;
    this.toolSelector = toolSelector;
    this.contextAssembler = contextAssembler;
    this.runStore = runStore;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.boundary = boundary;
    this.metrics = metrics;
  }

  @Override
  public String respondTo(String conversationId, String question) {
    return execute(
        conversationId,
        question,
        AgentRun.PromptType.CONVERSATIONAL,
        (context, selection) ->
            model.conversational(conversationId, context.prompt(), selection.tools()));
  }

  @Override
  public String respondTo(String conversationId, String question, AgentStreamListener listener) {
    return execute(
        conversationId,
        question,
        AgentRun.PromptType.CONVERSATIONAL,
        listener,
        (context, selection) ->
            model.conversational(
                conversationId, context.prompt(), selection.tools(), listener::onDelta));
  }

  @Override
  public <T> T prompt(String conversationId, String input, Class<T> resultType) {
    return execute(
        conversationId,
        input,
        AgentRun.PromptType.STRUCTURED,
        (context, selection) ->
            model.structured(conversationId, context.prompt(), selection.tools(), resultType));
  }

  @Override
  public TaskAgent.Result task(String conversationId, String input) {
    return execute(
        conversationId,
        input,
        AgentRun.PromptType.STRUCTURED,
        (context, selection) -> model.task(conversationId, context.prompt(), selection.tools()));
  }

  private <T> T execute(
      String conversationId,
      String input,
      AgentRun.PromptType promptType,
      Invocation<T> invocation) {
    return execute(conversationId, input, promptType, null, invocation);
  }

  private <T> T execute(
      String conversationId,
      String input,
      AgentRun.PromptType promptType,
      AgentStreamListener listener,
      Invocation<T> invocation) {
    Instant startedAt = clock.now();
    AgentToolSelection selection = toolSelector.select(conversationId, input);
    AgentContext context = contextAssembler.assemble(input, selection);
    try {
      T result =
          org.zalava.agent.ConversationChannelContext.call(
              conversationId, () -> invocation.call(context, selection));
      record(
          conversationId,
          input,
          promptType,
          selection,
          context,
          startedAt,
          AgentRun.Status.SUCCEEDED,
          result,
          null);
      if (listener != null && result instanceof String text) listener.onComplete(text);
      return result;
    } catch (RuntimeException failure) {
      record(
          conversationId,
          input,
          promptType,
          selection,
          context,
          startedAt,
          AgentRun.Status.FAILED,
          null,
          failure);
      if (listener != null) listener.onError(failure);
      throw failure;
    }
  }

  private void record(
      String conversationId,
      String input,
      AgentRun.PromptType promptType,
      AgentToolSelection selection,
      AgentContext context,
      Instant startedAt,
      AgentRun.Status status,
      Object result,
      RuntimeException failure) {
    Instant completedAt = clock.now();
    runStore.record(
        new AgentRun(
            idGenerator.nextId(),
            conversationId,
            promptType,
            redact(input),
            selection.tools().size(),
            context.sourceCount(),
            context.characterBudget(),
            context.charactersUsed(),
            context.sourceMetrics().stream()
                .map(
                    metric ->
                        new AgentRun.ContextSourceMetric(
                            metric.sourceType(),
                            metric.charactersAvailable(),
                            metric.charactersUsed()))
                .toList(),
            startedAt,
            completedAt,
            Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()),
            status,
            AgentRun.preview(redact(result)),
            AgentRun.preview(redact(AgentRun.errorPreview(failure))),
            promptType == AgentRun.PromptType.STRUCTURED
                ? StructuredRunEvidence.consumeOrDefault(1)
                : 0,
            structuredFailureCategory(promptType, failure)));
    try {
      metrics.agentRun(
          status.name().toLowerCase(java.util.Locale.ROOT),
          Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()),
          context.sourceMetrics().stream()
              .map(
                  metric ->
                      new OperationalMetrics.ContextUse(
                          metric.sourceType(), metric.charactersUsed()))
              .toList());
    } catch (RuntimeException ignored) {
      // Observability cannot change an agent result.
    }
  }

  private String redact(Object value) {
    if (value == null) return null;
    return boundary == null ? String.valueOf(value) : boundary.redact(String.valueOf(value));
  }

  private static AgentRun.StructuredFailureCategory structuredFailureCategory(
      AgentRun.PromptType promptType, RuntimeException failure) {
    if (promptType != AgentRun.PromptType.STRUCTURED || failure == null)
      return AgentRun.StructuredFailureCategory.NONE;
    if (failure instanceof StructuredOutputSchemaException) {
      return AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION;
    }
    String message = String.valueOf(failure.getMessage()).toLowerCase(java.util.Locale.ROOT);
    return message.contains("schema") || message.contains("structured output")
        ? AgentRun.StructuredFailureCategory.SCHEMA_VALIDATION
        : AgentRun.StructuredFailureCategory.MODEL_FAILURE;
  }

  @FunctionalInterface
  private interface Invocation<T> {
    T call(AgentContext context, AgentToolSelection selection);
  }
}
