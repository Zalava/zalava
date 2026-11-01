package org.zalava.capabilities.operation.adapter.out.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.chat.application.DirectChatApprovals;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;

class HostCallbackChatPolicyTest {
  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final TaskExecutionContext tasks = new TaskExecutionContext();
  private final ChatToolConfirmation confirmation = mock(ChatToolConfirmation.class);
  private final ZalavaToolApprovalRequests approvals = new ZalavaToolApprovalRequests();
  private final Actor first = new Actor(new AccountId(UUID.randomUUID()));
  private final Actor second = new Actor(new AccountId(UUID.randomUUID()));
  private final HostCallbackChatPolicy policy =
      new HostCallbackChatPolicy(actors, tasks, confirmation, approvals);

  @Test
  void approvalIsOneUseAndBoundToActorConversationAndExactInput() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(true);
    assertThat(
            call(
                first,
                "chat-a",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"one\"}")))
        .isPresent();
    var request = approvals.recentEntries().getFirst();
    approvals.approveChatHostOperation(request.requestId());
    assertThat(
            call(
                second,
                "chat-a",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"one\"}")))
        .isPresent();
    assertThat(
            call(
                first,
                "chat-b",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"one\"}")))
        .isPresent();
    assertThat(
            call(
                first,
                "chat-a",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"two\"}")))
        .isPresent();
    assertThat(
            call(
                first,
                "chat-a",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"one\"}")))
        .isEmpty();
    assertThat(
            call(
                first,
                "chat-a",
                () -> policy.beforeCall("beginModuleDevelopment", "{\"requestId\":\"one\"}")))
        .isPresent();
  }

  @Test
  void revokedOrDeniedApprovalNeverPermitsTheCallback() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isPresent();
    approvals.approveChatHostOperation(approvals.recentEntries().getFirst().requestId());
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(false);
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isPresent();
    var denied = approvals.recentEntries().getFirst();
    approvals.denyUnscoped(denied.requestId());
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(true);
    assertThat(call(first, "chat-b", () -> policy.beforeCall("proposeMemory", "{}"))).isPresent();
  }

  @Test
  void defaultsOtherChannelsAndJobsPreserveTheirExistingBehavior() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.empty());
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isEmpty();
    assertThat(call(first, "telegram-1", () -> policy.beforeCall("proposeMemory", "{}"))).isEmpty();
    assertThat(
            call(
                first,
                "chat-a",
                () ->
                    tasks.call(
                        TaskReference.parse("2026-11-01", "120000-example.md"),
                        () -> policy.beforeCall("proposeMemory", "{}"))))
        .isEmpty();
    assertThat(call(first, "chat-a", () -> policy.beforeCall("unregisteredTool", "{}"))).isEmpty();
    assertThat(approvals.recentEntries()).isEmpty();
  }

  @Test
  void concurrentRetriesConsumeOnlyOneApproval() throws Exception {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(true);
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isPresent();
    approvals.approveChatHostOperation(approvals.recentEntries().getFirst().requestId());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(() -> call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}")));
      var two =
          pool.submit(() -> call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}")));
      assertThat(List.of(one.get(), two.get()).stream().filter(Optional::isEmpty)).hasSize(1);
    }
  }

  @Test
  void directApprovalApplicationUseCaseEnforcesOwnership() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(true);
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isPresent();
    var request = approvals.recentEntries().getFirst();
    var direct = new DirectChatApprovals(approvals, mock(ProviderToolOperations.class));
    assertThat(direct.pending(first, AccountRole.MEMBER)).hasSize(1);
    assertThat(direct.pending(second, AccountRole.MEMBER)).isEmpty();
    assertThatThrownBy(() -> direct.decide(second, AccountRole.MEMBER, request.requestId(), true))
        .isInstanceOf(ZalavaToolApprovalRequests.NotFoundException.class);
    assertThat(direct.decide(first, AccountRole.MEMBER, request.requestId(), true))
        .contains("Action approved");
    assertThat(call(first, "chat-a", () -> policy.beforeCall("proposeMemory", "{}"))).isEmpty();
  }

  private <T> T call(Actor actor, String conversation, Supplier<T> action) {
    return actors.call(
        actor, AccountRole.MEMBER, () -> ConversationChannelContext.call(conversation, action));
  }
}
