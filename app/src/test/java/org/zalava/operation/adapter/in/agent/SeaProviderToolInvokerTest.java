package org.zalava.operation.adapter.in.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.SeaOperationResult;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.ConversationChannelContext;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.in.ProviderToolOperationException;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;

class SeaProviderToolInvokerTest {

  private final TaskExecutionContext taskExecutionContext = new TaskExecutionContext();

  @Test
  void returnsExecutedResultsWithTaskCorrelation() {
    RecordingOperations operations =
        new RecordingOperations(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                SeaOperationResult.success(Map.of("value", "ok"))));
    SeaProviderToolInvoker invoker = new SeaProviderToolInvoker(operations, taskExecutionContext);
    TaskReference reference = TaskReference.parse("2026-06-14", "120000-read.md");

    SeaOperationResult result =
        taskExecutionContext.call(
            reference,
            () -> invoker.invoke("provider", "read", "{\"path\":\"a.txt\"}", "spring-ai-callback"));

    assertThat(result.success()).isTrue();
    assertThat(operations.command.taskReference()).isEqualTo(reference);
    assertThat(operations.command.context().attributes())
        .containsEntry("source", "spring-ai-callback")
        .containsEntry("taskReference", reference.path());
  }

  @Test
  void derivesActorRoleAndOwnedTaskCorrelationFromTrustedExecutionContexts() {
    RecordingOperations operations =
        new RecordingOperations(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                SeaOperationResult.success(Map.of("value", "ok"))));
    ActorExecutionContext actorExecution = new ActorExecutionContext();
    SeaProviderToolInvoker invoker =
        new SeaProviderToolInvoker(operations, taskExecutionContext, actorExecution);
    Actor actor = new Actor(AccountId.newId());
    ActorTaskExecutionReference task =
        new ActorTaskExecutionReference(actor, ActorTaskReference.newReference());

    actorExecution.call(
        actor,
        AccountRole.MEMBER,
        () ->
            taskExecutionContext.call(
                task, () -> invoker.invoke("provider", "read", "{}", "spring-ai-callback")));

    assertThat(operations.command.taskReference()).isNull();
    assertThat(operations.command.context().actorId()).isEqualTo(actor.accountId().toString());
    assertThat(operations.command.context().attributes())
        .containsEntry("accountRole", "MEMBER")
        .containsEntry(SeaToolApprovalRequests.ACTOR_TASK_REFERENCE, task.encode());
  }

  @Test
  void usesBoundConversationChannelInsteadOfUntrustedSourceText() {
    RecordingOperations operations =
        new RecordingOperations(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                SeaOperationResult.success(Map.of("value", "ok"))));
    SeaProviderToolInvoker invoker = new SeaProviderToolInvoker(operations, taskExecutionContext);

    ConversationChannelContext.call(
        "telegram-123", () -> invoker.invoke("provider", "read", "{}", "channel=web"));

    assertThat(operations.command.context().attributes())
        .containsEntry("source", "channel=web")
        .containsEntry("permissionPolicyChannel", "telegram");
  }

  @Test
  void mapsPendingAndDeniedApprovalsToStructuredResults() {
    ToolApproval approval = approval("request-1", ToolApproval.Decision.PENDING);
    SeaProviderToolInvoker pendingInvoker =
        new SeaProviderToolInvoker(
            new RecordingOperations(ProviderToolOperations.ToolInvocationOutcome.pending(approval)),
            taskExecutionContext);
    SeaProviderToolInvoker deniedInvoker =
        new SeaProviderToolInvoker(
            new RecordingOperations(
                ProviderToolOperations.ToolInvocationOutcome.denied(
                    approval("request-2", ToolApproval.Decision.DENIED))),
            taskExecutionContext);

    assertThat(pendingInvoker.invoke("provider", "write", "{}", "spring-ai-callback").content())
        .isEqualTo(
            Map.of(
                "status", "pending_approval",
                "approvalRequestId", "request-1",
                "jobReference", ""));
    assertThat(deniedInvoker.invoke("provider", "write", "{}", "spring-ai-callback").content())
        .isEqualTo(Map.of("status", "denied", "approvalRequestId", "request-2"));
  }

  @Test
  void preservesAgentFacingExceptionMapping() {
    ProviderToolOperations operations =
        new ProviderToolOperations() {
          @Override
          public ToolInvocationOutcome invoke(ToolInvocationCommand command) {
            throw new ProviderToolOperationException(
                ProviderToolOperationException.Code.VALIDATION, "invalid arguments");
          }

          @Override
          public ToolInvocationOutcome allowUnscoped(String requestId) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ToolInvocationOutcome allowUnscopedTool(String requestId) {
            throw new UnsupportedOperationException();
          }

          @Override
          public ToolApproval denyUnscoped(String requestId) {
            throw new UnsupportedOperationException();
          }
        };

    assertThatThrownBy(
            () ->
                new SeaProviderToolInvoker(operations, taskExecutionContext)
                    .invoke("provider", "read", "{}", "spring-ai-callback"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid arguments");
  }

  private static ToolApproval approval(String requestId, ToolApproval.Decision decision) {
    return new ToolApproval(
        requestId,
        "provider",
        "write",
        "agent",
        Map.of(),
        Map.of(),
        "{}",
        null,
        decision,
        ToolApproval.ApprovalScope.ONCE);
  }

  private static final class RecordingOperations implements ProviderToolOperations {

    private final ToolInvocationOutcome outcome;
    private ToolInvocationCommand command;

    private RecordingOperations(ToolInvocationOutcome outcome) {
      this.outcome = outcome;
    }

    @Override
    public ToolInvocationOutcome invoke(ToolInvocationCommand command) {
      this.command = command;
      return outcome;
    }

    @Override
    public ToolInvocationOutcome allowUnscoped(String requestId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ToolInvocationOutcome allowUnscopedTool(String requestId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ToolApproval denyUnscoped(String requestId) {
      throw new UnsupportedOperationException();
    }
  }
}
