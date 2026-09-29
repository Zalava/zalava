package org.zalava.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.in.ProviderToolOperationException;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.operation.application.port.out.ToolApprovalPort;
import org.zalava.operation.application.port.out.ToolInvocationObservation;
import org.zalava.operation.application.port.out.ToolInvocationObserver;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.JsonNode;

class DefaultProviderToolOperationsTest {

  private static final InvocationContext OPERATOR =
      new InvocationContext("operator-1", false, Map.of("source", "test"));

  private final MutableProvider provider = new MutableProvider();
  private final InMemoryApprovals approvals = new InMemoryApprovals();
  private final RecordingObserver observer = new RecordingObserver();
  private final ProviderToolOperations operations =
      new DefaultProviderToolOperations(
          providerId ->
              "test-provider".equals(providerId) ? Optional.of(provider) : Optional.empty(),
          approvals,
          List.of(observer));

  @Test
  void executesSafeToolAndRecordsSuccess() {
    ProviderToolOperations.ToolInvocationOutcome outcome =
        operations.invoke(
            ProviderToolOperations.ToolInvocationCommand.operator(
                "test-provider", "read", "{\"path\":\"notes/a.txt\"}", OPERATOR));

    assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(outcome.result().content())
        .isEqualTo(Map.of("path", "notes/a.txt", "confirmed", false));
    assertThat(provider.calls).hasValue(1);
    assertThat(observer.observations)
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.success()).isTrue();
              assertThat(observation.providerId()).isEqualTo("test-provider");
              assertThat(observation.toolName()).isEqualTo("read");
            });
  }

  @Test
  void createsUnscopedApprovalForOperatorSideEffect() {
    ProviderToolOperations.ToolInvocationOutcome outcome =
        operations.invoke(
            ProviderToolOperations.ToolInvocationCommand.operator(
                "test-provider", "write", "{\"path\":\"notes/a.txt\"}", OPERATOR));

    assertThat(outcome.status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(outcome.approval().taskReference()).isNull();
    assertThat(provider.calls).hasValue(0);
    assertThat(observer.observations).isEmpty();
  }

  @Test
  void allowsUnscopedApprovalAndExecutesWithConfirmedContext() {
    String requestId =
        operations
            .invoke(
                ProviderToolOperations.ToolInvocationCommand.operator(
                    "test-provider", "write", "{\"path\":\"notes/a.txt\"}", OPERATOR))
            .approval()
            .requestId();

    ProviderToolOperations.ToolInvocationOutcome outcome = operations.allowUnscoped(requestId);

    assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(outcome.result().content())
        .isEqualTo(Map.of("path", "notes/a.txt", "confirmed", true));
    assertThat(provider.calls).hasValue(1);
    assertThat(observer.observations)
        .singleElement()
        .satisfies(observation -> assertThat(observation.confirmed()).isTrue());
  }

  @Test
  void consumesAllowedTaskApprovalOnNextInvocation() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    ProviderToolOperations.ToolInvocationCommand command =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of("source", "agent")),
            reference);
    String requestId = operations.invoke(command).approval().requestId();
    approvals.allow(requestId, reference);

    ProviderToolOperations.ToolInvocationOutcome outcome = operations.invoke(command);

    assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(outcome.result().content())
        .isEqualTo(Map.of("path", "notes/a.txt", "confirmed", true));
    assertThat(approvals.consumedRequestIds).containsExactly(requestId);
  }

  @Test
  void consumesDeniedTaskApprovalWithoutCallingProvider() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    ProviderToolOperations.ToolInvocationCommand command =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of()),
            reference);
    String requestId = operations.invoke(command).approval().requestId();
    approvals.deny(requestId, reference);

    ProviderToolOperations.ToolInvocationOutcome outcome = operations.invoke(command);

    assertThat(outcome.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.DENIED);
    assertThat(outcome.approval().requestId()).isEqualTo(requestId);
    assertThat(provider.calls).hasValue(0);
    assertThat(observer.observations)
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.success()).isFalse();
              assertThat(observation.errorType()).isEqualTo("permission_denied");
              assertThat(observation.attributes())
                  .containsEntry("approvalDecision", "denied")
                  .containsEntry("approvalRequestId", requestId);
            });
  }

  @Test
  void returnsAlreadyExecutedForRepeatedUnscopedApprovalRequest() {
    ProviderToolOperations.ToolInvocationCommand command =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of("source", "telegram")),
            null);
    String requestId = operations.invoke(command).approval().requestId();
    operations.allowUnscoped(requestId);

    ProviderToolOperations.ToolInvocationOutcome repeated = operations.invoke(command);

    assertThat(repeated.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) repeated.result().content();
    assertThat(content)
        .containsEntry("status", "already_executed")
        .containsEntry("approvalRequestId", requestId);
    assertThat(provider.calls).hasValue(1);
    assertThat(approvals.requests).hasSize(1);
  }

  @Test
  void returnsDeniedForRepeatedDeniedUnscopedApprovalRequest() {
    ProviderToolOperations.ToolInvocationCommand command =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of("source", "telegram")),
            null);
    String requestId = operations.invoke(command).approval().requestId();
    operations.denyUnscoped(requestId);

    ProviderToolOperations.ToolInvocationOutcome repeated = operations.invoke(command);

    assertThat(repeated.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.DENIED);
    assertThat(repeated.approval().requestId()).isEqualTo(requestId);
    assertThat(provider.calls).hasValue(0);
    assertThat(approvals.requests).hasSize(1);
  }

  @Test
  void executesFutureMatchingToolCallsWhenToolPolicyWasAllowed() {
    ProviderToolOperations.ToolInvocationCommand initial =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of("source", "telegram")),
            null);
    String requestId = operations.invoke(initial).approval().requestId();

    ProviderToolOperations.ToolInvocationOutcome approved = operations.allowUnscopedTool(requestId);
    ProviderToolOperations.ToolInvocationOutcome future =
        operations.invoke(
            ProviderToolOperations.ToolInvocationCommand.task(
                "test-provider",
                "write",
                "{\"path\":\"notes/other.txt\"}",
                new InvocationContext("agent", false, Map.of("source", "telegram")),
                null));

    assertThat(approved.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(future.status()).isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(future.result().content())
        .isEqualTo(Map.of("path", "notes/other.txt", "confirmed", true));
    assertThat(provider.calls).hasValue(2);
    assertThat(approvals.requests).hasSize(1);
    assertThat(observer.observations.getLast().attributes())
        .containsEntry("approvalPolicy", "tool")
        .containsEntry("approvalRequestId", requestId);
  }

  @Test
  void ignoresToolPolicyWhenProviderScopeChanges() {
    ProviderToolOperations.ToolInvocationCommand initial =
        ProviderToolOperations.ToolInvocationCommand.task(
            "test-provider",
            "write",
            "{\"path\":\"notes/a.txt\"}",
            new InvocationContext("agent", false, Map.of("source", "telegram")),
            null);
    String requestId = operations.invoke(initial).approval().requestId();
    operations.allowUnscopedTool(requestId);
    provider.scope = Map.of("root", "other-workspace");

    ProviderToolOperations.ToolInvocationOutcome future =
        operations.invoke(
            ProviderToolOperations.ToolInvocationCommand.task(
                "test-provider",
                "write",
                "{\"path\":\"notes/other.txt\"}",
                new InvocationContext("agent", false, Map.of("source", "telegram")),
                null));

    assertThat(future.status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(provider.calls).hasValue(1);
    assertThat(approvals.requests).hasSize(2);
  }

  @Test
  void normalizesProviderFailureAndRecordsIt() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "test-provider", "fail", "{}", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.EXECUTION));

    assertThat(observer.observations)
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.success()).isFalse();
              assertThat(observation.errorType()).isEqualTo("execution");
              assertThat(observation.errorMessage()).isEqualTo("provider failed");
            });
  }

  @Test
  void reportsMissingProviderThroughTypedApplicationError() {
    assertThatThrownBy(
            () ->
                operations.invoke(
                    ProviderToolOperations.ToolInvocationCommand.operator(
                        "missing", "read", "{}", OPERATOR)))
        .isInstanceOfSatisfying(
            ProviderToolOperationException.class,
            exception ->
                assertThat(exception.code())
                    .isEqualTo(ProviderToolOperationException.Code.PROVIDER_NOT_FOUND));
  }

  private static final class RecordingObserver implements ToolInvocationObserver {

    private final List<ToolInvocationObservation> observations = new ArrayList<>();

    @Override
    public void observe(ToolInvocationObservation observation) {
      observations.add(observation);
    }
  }

  private static final class InMemoryApprovals implements ToolApprovalPort {

    private final List<ToolApproval> requests = new ArrayList<>();
    private final List<String> consumedRequestIds = new ArrayList<>();
    private int sequence;

    @Override
    public ToolApproval create(
        ZalavaProvider provider,
        ZalavaToolDescriptor tool,
        InvocationContext context,
        String argumentsJson,
        TaskReference taskReference) {
      ToolApproval existing =
          requests.stream()
              .filter(request -> request.decision() == ToolApproval.Decision.PENDING)
              .filter(request -> java.util.Objects.equals(request.taskReference(), taskReference))
              .filter(request -> request.providerId().equals(provider.descriptor().providerId()))
              .filter(request -> request.toolName().equals(tool.name()))
              .filter(request -> request.argumentsJson().equals(argumentsJson))
              .findFirst()
              .orElse(null);
      if (existing != null) {
        return existing;
      }
      ToolApproval created =
          new ToolApproval(
              "request-" + ++sequence,
              provider.descriptor().providerId(),
              tool.name(),
              context.actorId(),
              context.attributes(),
              provider.descriptor().scope(),
              argumentsJson,
              taskReference,
              ToolApproval.Decision.PENDING,
              ToolApproval.ApprovalScope.ONCE);
      requests.add(created);
      return created;
    }

    @Override
    public Optional<ToolApproval> consumeDecision(
        TaskReference taskReference, String providerId, String toolName, String argumentsJson) {
      return requests.stream()
          .filter(
              request ->
                  request.taskReference() != null && request.taskReference().equals(taskReference))
          .filter(request -> request.providerId().equals(providerId))
          .filter(request -> request.toolName().equals(toolName))
          .filter(request -> request.argumentsJson().equals(argumentsJson))
          .filter(request -> request.decision() != ToolApproval.Decision.PENDING)
          .filter(request -> !consumedRequestIds.contains(request.requestId()))
          .findFirst()
          .map(
              request -> {
                consumedRequestIds.add(request.requestId());
                return request;
              });
    }

    @Override
    public Optional<ToolApproval> findUnscopedDecision(
        String actorId, String providerId, String toolName, String argumentsJson) {
      return requests.stream()
          .filter(request -> request.taskReference() == null)
          .filter(request -> request.actorId().equals(actorId))
          .filter(request -> request.providerId().equals(providerId))
          .filter(request -> request.toolName().equals(toolName))
          .filter(request -> request.argumentsJson().equals(argumentsJson))
          .filter(request -> request.decision() != ToolApproval.Decision.PENDING)
          .findFirst();
    }

    @Override
    public Optional<ToolApproval> findAllowedToolPolicy(
        String actorId, String providerId, String toolName, Map<String, String> providerScope) {
      return requests.stream()
          .filter(request -> request.approvalScope() == ToolApproval.ApprovalScope.TOOL)
          .filter(request -> request.decision() == ToolApproval.Decision.ALLOWED)
          .filter(request -> request.actorId().equals(actorId))
          .filter(request -> request.providerId().equals(providerId))
          .filter(request -> request.toolName().equals(toolName))
          .filter(request -> request.providerScope().equals(providerScope))
          .findFirst();
    }

    @Override
    public ToolApproval allowUnscoped(String requestId) {
      return decide(
          requestId, null, ToolApproval.Decision.ALLOWED, ToolApproval.ApprovalScope.ONCE);
    }

    @Override
    public ToolApproval allowUnscopedTool(String requestId) {
      return decide(
          requestId, null, ToolApproval.Decision.ALLOWED, ToolApproval.ApprovalScope.TOOL);
    }

    @Override
    public ToolApproval denyUnscoped(String requestId) {
      return decide(requestId, null, ToolApproval.Decision.DENIED, ToolApproval.ApprovalScope.ONCE);
    }

    void allow(String requestId, TaskReference reference) {
      decide(requestId, reference, ToolApproval.Decision.ALLOWED, ToolApproval.ApprovalScope.ONCE);
    }

    void deny(String requestId, TaskReference reference) {
      decide(requestId, reference, ToolApproval.Decision.DENIED, ToolApproval.ApprovalScope.ONCE);
    }

    private ToolApproval decide(
        String requestId,
        TaskReference expectedReference,
        ToolApproval.Decision decision,
        ToolApproval.ApprovalScope approvalScope) {
      for (int i = 0; i < requests.size(); i++) {
        ToolApproval request = requests.get(i);
        if (!request.requestId().equals(requestId)) {
          continue;
        }
        assertThat(request.taskReference()).isEqualTo(expectedReference);
        ToolApproval decided =
            new ToolApproval(
                request.requestId(),
                request.providerId(),
                request.toolName(),
                request.actorId(),
                request.attributes(),
                request.providerScope(),
                request.argumentsJson(),
                request.taskReference(),
                decision,
                approvalScope);
        requests.set(i, decided);
        return decided;
      }
      throw new ApprovalNotFoundException(requestId);
    }
  }

  private static final class MutableProvider implements ZalavaProvider {

    private final AtomicInteger calls = new AtomicInteger();
    private Map<String, String> scope = Map.of("root", "workspace");

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "test-provider",
          "test-module",
          "test",
          "Test Provider",
          "Provider for application use case tests.",
          "1.0.0",
          capabilities(),
          List.of("test"),
          scope);
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(
          new ZalavaToolDescriptor("read", "Reads.", false, null),
          new ZalavaToolDescriptor("write", "Writes.", true, null),
          new ZalavaToolDescriptor("fail", "Fails.", false, null));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, JsonNode arguments, InvocationContext context) {
      if ("fail".equals(toolName)) {
        throw new IllegalStateException("provider failed");
      }
      calls.incrementAndGet();
      return ZalavaOperationResult.success(
          Map.of(
              "path", arguments.path("path").stringValue(""),
              "confirmed", context.confirmed()));
    }
  }
}
