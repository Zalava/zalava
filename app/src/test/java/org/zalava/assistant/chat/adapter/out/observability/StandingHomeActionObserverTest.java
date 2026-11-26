package org.zalava.assistant.chat.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore.Evidence;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.InvocationChannelContext;

class StandingHomeActionObserverTest {
  @Test
  void retainsOnlyValidatedStandingGrantEvidenceAndDoesNotPropagateStorageFailure() {
    var store = mock(StandingHomeActionEvidenceStore.class);
    var observer = new StandingHomeActionObserver(store);
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    String conversation =
        new ActorConversationId(actor, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    var attributes =
        Map.of(
            "approvalPolicy",
            "tool",
            "zalavaAuditHomeModuleId",
            "zalava-module-home-assistant",
            "permissionPolicyChannel",
            "web",
            "conversationId",
            conversation,
            ToolInvocationObservation.HOME_AUDIT_EXECUTION,
            UUID.randomUUID().toString(),
            InvocationChannelContext.RUN_ID,
            UUID.randomUUID().toString());
    var arguments =
        Map.<String, Object>of(
            "domain",
            "light",
            "action",
            "turn_on",
            "entityId",
            "light.room",
            "serviceData",
            Map.of("token", "secret-value"));
    var observation =
        observation(
            actor,
            attributes,
            arguments,
            ZalavaOperationResult.success(Map.of("private", "secret-value")));
    observer.observe(observation);
    var captured = ArgumentCaptor.forClass(Evidence.class);
    verify(store).record(any(), captured.capture());
    assertThat(captured.getValue().toString())
        .contains("light.room")
        .doesNotContain("secret-value");
    doThrow(new IllegalStateException("offline")).when(store).record(any(), any());
    assertThatCode(() -> observer.observe(observation)).doesNotThrowAnyException();
  }

  @Test
  void skipsMismatchedActorAndModuleButRetainsAnObservedExecutionException() {
    var store = mock(StandingHomeActionEvidenceStore.class);
    var observer = new StandingHomeActionObserver(store);
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    Actor other = new Actor(new AccountId(UUID.randomUUID()));
    String foreign =
        new ActorConversationId(other, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    var attributes =
        Map.of(
            "approvalPolicy",
            "tool",
            "zalavaAuditHomeModuleId",
            "zalava-module-home-assistant",
            "permissionPolicyChannel",
            "web",
            "conversationId",
            foreign,
            ToolInvocationObservation.HOME_AUDIT_EXECUTION,
            UUID.randomUUID().toString(),
            InvocationChannelContext.RUN_ID,
            UUID.randomUUID().toString());
    var arguments =
        Map.<String, Object>of("domain", "light", "action", "turn_on", "entityId", "light.room");
    observer.observe(
        observation(actor, attributes, arguments, ZalavaOperationResult.success(Map.of())));
    String owned =
        new ActorConversationId(actor, new ConversationReference(UUID.randomUUID().toString()))
            .value();
    observer.observe(
        observation(
            actor,
            Map.of(
                "approvalPolicy",
                "tool",
                "zalavaAuditHomeModuleId",
                "unrelated-module",
                "permissionPolicyChannel",
                "web",
                "conversationId",
                owned,
                ToolInvocationObservation.HOME_AUDIT_EXECUTION,
                UUID.randomUUID().toString(),
                InvocationChannelContext.RUN_ID,
                UUID.randomUUID().toString()),
            arguments,
            ZalavaOperationResult.success(Map.of())));
    observer.observe(
        new ToolInvocationObservation(
            "home-assistant",
            "home_assistant_execute_action",
            actor.accountId().toString(),
            Map.of(
                "approvalPolicy",
                "tool",
                "zalavaAuditHomeModuleId",
                "zalava-module-home-assistant",
                "permissionPolicyChannel",
                "web",
                "conversationId",
                owned,
                ToolInvocationObservation.HOME_AUDIT_EXECUTION,
                UUID.randomUUID().toString(),
                InvocationChannelContext.RUN_ID,
                UUID.randomUUID().toString()),
            true,
            "home",
            List.of(),
            true,
            false,
            "permission_denied",
            null,
            null,
            0,
            arguments));
    verifyNoInteractions(store);
    observer.observe(
        observation(
            actor,
            Map.of(
                "approvalPolicy",
                "tool",
                "zalavaAuditHomeModuleId",
                "zalava-module-home-assistant",
                "permissionPolicyChannel",
                "web",
                "conversationId",
                owned,
                ToolInvocationObservation.HOME_AUDIT_EXECUTION,
                UUID.randomUUID().toString(),
                InvocationChannelContext.RUN_ID,
                UUID.randomUUID().toString()),
            arguments,
            null));
    var captured = ArgumentCaptor.forClass(Evidence.class);
    verify(store).record(any(), captured.capture());
    assertThat(captured.getValue().success()).isFalse();
  }

  private static ToolInvocationObservation observation(
      Actor actor,
      Map<String, String> attributes,
      Map<String, Object> arguments,
      ZalavaOperationResult result) {
    return new ToolInvocationObservation(
        "home-assistant",
        "home_assistant_execute_action",
        actor.accountId().toString(),
        attributes,
        true,
        "home",
        List.of(),
        true,
        result != null && result.success(),
        result == null ? "execution" : null,
        null,
        result,
        12,
        arguments);
  }
}
