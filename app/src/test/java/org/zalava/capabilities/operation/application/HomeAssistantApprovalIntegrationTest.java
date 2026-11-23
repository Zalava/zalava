package org.zalava.capabilities.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.adapter.out.approval.ZalavaToolApprovalAdapter;
import org.zalava.capabilities.operation.adapter.out.json.JacksonToolArgumentDecoder;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

class HomeAssistantApprovalIntegrationTest {
  private static final String TOOL = "home_assistant_execute_action";
  private final UUID actorId = UUID.randomUUID();
  private final AccountLifecycle accounts = mock(AccountLifecycle.class);
  private final ZalavaToolApprovalRequests approvals = new ZalavaToolApprovalRequests();
  private final AtomicInteger calls = new AtomicInteger();
  private final InvocationContext actor =
      new InvocationContext(
          actorId.toString(),
          false,
          Map.of(
              "accountRole",
              "ADMIN",
              "permissionPolicyChannel",
              "web",
              "conversationId",
              "one",
              "zalava.runId",
              "run-one"));
  private final ZalavaProvider provider =
      new ZalavaProvider() {
        public ProviderDescriptor descriptor() {
          return new ProviderDescriptor(
              "ha",
              "zalava-module-home-assistant",
              "home-automation",
              "Home",
              "Home",
              "1",
              capabilities(),
              List.of("zalava_backed"),
              Map.of("endpoint", "one"));
        }

        public ProviderCapabilities capabilities() {
          return ProviderCapabilities.toolsOnly();
        }

        public List<ZalavaToolDescriptor> listTools() {
          return List.of(new ZalavaToolDescriptor(TOOL, "Action", true));
        }

        public ZalavaOperationResult callTool(
            String tool, Map<String, Object> arguments, InvocationContext context) {
          calls.incrementAndGet();
          return ZalavaOperationResult.success(Map.of("accepted", true));
        }
      };
  private final DefaultProviderToolOperations operations =
      new DefaultProviderToolOperations(
          id -> Optional.of(provider),
          new ZalavaToolApprovalAdapter(approvals),
          List.of(),
          new MemberProviderCapabilityPolicy(),
          new JacksonToolArgumentDecoder(),
          null,
          accounts);

  private void account(boolean enabled) {
    var id = new AccountId(actorId);
    when(accounts.findById(id))
        .thenReturn(
            Optional.of(
                new Account(
                    id,
                    "admin",
                    "hash",
                    enabled,
                    AccountRole.ADMIN,
                    false,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
  }

  private ProviderToolOperations.ToolInvocationCommand command(String target, int brightness) {
    return ProviderToolOperations.ToolInvocationCommand.task(
        "ha",
        TOOL,
        "{\"domain\":\"light\",\"action\":\"turn_on\",\"entityId\":\""
            + target
            + "\",\"serviceData\":{\"brightness\":"
            + brightness
            + "}}",
        actor,
        null);
  }

  @Test
  void durableApprovalDoesNotAuthorizeChangedTargetOrParametersAndCanBeRevoked() {
    account(true);
    var request = operations.invoke(command("light.room", 10)).approval();
    operations.allowUnscopedTool(request.requestId());
    assertThat(calls).hasValue(1);
    assertThat(operations.invoke(command("light.room", 10)).status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
    assertThat(calls).hasValue(2);
    assertThat(operations.invoke(command("light.other", 10)).status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(operations.invoke(command("light.room", 20)).status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    approvals.revokeToolPolicy(request.requestId());
    assertThat(operations.invoke(command("light.room", 10)).status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(calls).hasValue(2);
  }

  @Test
  void disablingActorAfterRequestPreventsApprovedDispatch() {
    account(true);
    var request = operations.invoke(command("light.room", 10)).approval();
    account(false);
    assertThatThrownBy(() -> operations.allowUnscoped(request.requestId()))
        .hasMessageContaining("denied");
    assertThat(calls).hasValue(0);
    assertThat(approvals.get(request.requestId()).decision())
        .isEqualTo(ZalavaToolApprovalRequests.Decision.PENDING);
  }

  @Test
  void aDurableDecisionCannotBeUsedInAnotherConversation() {
    account(true);
    var original = command("light.room", 10);
    var request = operations.invoke(original).approval();
    operations.allowUnscopedTool(request.requestId());
    var anotherConversation =
        new InvocationContext(
            actorId.toString(),
            false,
            Map.of(
                "accountRole",
                "ADMIN",
                "permissionPolicyChannel",
                "web",
                "conversationId",
                "two",
                "zalava.runId",
                "run-two"));
    var other =
        ProviderToolOperations.ToolInvocationCommand.task(
            "ha", TOOL, original.argumentsJson(), anotherConversation, null);
    assertThat(operations.invoke(other).status())
        .isEqualTo(ProviderToolOperations.ToolInvocationStatus.PENDING_APPROVAL);
    assertThat(calls).hasValue(1);
  }

  @Test
  void revocationBetweenPolicySelectionAndDispatchPreventsExecution() {
    account(true);
    var adapter = spy(new ZalavaToolApprovalAdapter(approvals));
    var guarded =
        new DefaultProviderToolOperations(
            id -> Optional.of(provider),
            adapter,
            List.of(),
            new MemberProviderCapabilityPolicy(),
            new JacksonToolArgumentDecoder(),
            null,
            accounts);
    var request = guarded.invoke(command("light.room", 10)).approval();
    guarded.allowUnscopedTool(request.requestId());
    doAnswer(
            invocation -> {
              approvals.revokeToolPolicy(request.requestId());
              return invocation.callRealMethod();
            })
        .when(adapter)
        .isScopedPolicyActive(anyString(), any(), any(), any(), any());
    assertThatThrownBy(() -> guarded.invoke(command("light.room", 10)))
        .hasMessageContaining("revoked or changed");
    assertThat(calls).hasValue(1);
  }

  @Test
  void revokingMemberToolAccessAfterApprovalRequestPreventsDispatch() {
    var id = new AccountId(actorId);
    when(accounts.findById(id))
        .thenReturn(
            Optional.of(
                new Account(
                    id,
                    "member",
                    "hash",
                    true,
                    AccountRole.MEMBER,
                    false,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
    var access = mock(MemberToolAccess.class);
    when(access.allowed(anyString(), any())).thenReturn(true);
    var guarded =
        new DefaultProviderToolOperations(
            ignored -> Optional.of(provider),
            new ZalavaToolApprovalAdapter(approvals),
            List.of(),
            new MemberProviderCapabilityPolicy(access),
            new JacksonToolArgumentDecoder(),
            null,
            accounts);
    var member =
        new InvocationContext(
            actorId.toString(),
            false,
            Map.of(
                "accountRole",
                "MEMBER",
                "permissionPolicyChannel",
                "web",
                "conversationId",
                "member-conversation",
                "zalava.runId",
                "member-run"));
    var command = command("light.room", 10);
    var memberCommand =
        ProviderToolOperations.ToolInvocationCommand.task(
            "ha", TOOL, command.argumentsJson(), member, null);
    var request = guarded.invoke(memberCommand).approval();
    when(access.allowed(anyString(), any())).thenReturn(false);
    assertThatThrownBy(() -> guarded.allowUnscopedTool(request.requestId()))
        .hasMessageContaining("denied");
    assertThat(approvals.get(request.requestId()).decision())
        .isEqualTo(ZalavaToolApprovalRequests.Decision.PENDING);
    assertThat(calls).hasValue(0);
  }

  @Test
  void refusesRequestsTooLargeToReviewAndPreservesStringWhitespaceInPreview() {
    account(true);
    var oversized =
        ProviderToolOperations.ToolInvocationCommand.task(
            "ha",
            TOOL,
            "{\"domain\":\"light\",\"action\":\"turn_on\",\"entityId\":\"light.room\",\"serviceData\":{\"name\":\""
                + "x".repeat(1000)
                + "\"}}",
            actor,
            null);
    assertThatThrownBy(() -> operations.invoke(oversized))
        .hasMessageContaining("too large to review");
    var spaced =
        ProviderToolOperations.ToolInvocationCommand.task(
            "ha",
            TOOL,
            "{\"domain\":\"light\",\"action\":\"turn_on\",\"entityId\":\"light.room\",\"serviceData\":{\"name\":\"a  b\"}}",
            actor,
            null);
    var request = operations.invoke(spaced).approval();
    assertThat(approvals.get(request.requestId()).summary().argumentsPreview()).contains("a  b");
    assertThat(calls).hasValue(0);
  }
}
