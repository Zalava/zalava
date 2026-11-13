package org.zalava.assistant.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.ApprovalScope;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Decision;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Entry;
import org.zalava.capabilities.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import tools.jackson.databind.ObjectMapper;

class ChatApprovalContextTest {
  @TempDir Path workspace;
  private final Actor actor = new Actor(new AccountId(UUID.randomUUID()));
  private final Actor other = new Actor(new AccountId(UUID.randomUUID()));
  private final String conversation =
      new ActorConversationId(actor, ConversationReference.newReference()).value();
  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void projectsPersistedDecisionsWithoutArgumentsAndKeepsUnknownSeparate() {
    save("pending", Decision.PENDING, Map.of(), true, actor, conversation);
    save("denied", Decision.DENIED, Map.of(), true, actor, conversation);
    save("revoked", Decision.REVOKED, Map.of(), true, actor, conversation);
    save(
        "success",
        Decision.ALLOWED,
        Map.of("approvalExecutionOutcome", "succeeded"),
        true,
        actor,
        conversation);
    save(
        "failure",
        Decision.ALLOWED,
        Map.of("approvalExecutionOutcome", "failed"),
        true,
        actor,
        conversation);
    save("unknown", Decision.ALLOWED, Map.of(), true, actor, conversation);
    save("read", Decision.ALLOWED, Map.of(), false, actor, conversation);
    save(
        "callback",
        Decision.ALLOWED,
        Map.of("hostCallbackRetry", "true"),
        true,
        actor,
        conversation);
    String result = render(actor, conversation);
    assertThat(result)
        .contains(
            "approval pending",
            "approval denied",
            "approval revoked",
            "approved; execution succeeded",
            "approved; execution failed",
            "approved; execution outcome unknown",
            "decision required a later retry, execution is not established here")
        .doesNotContain("sensitive-argument");
  }

  @Test
  void linksTheOriginalUserRequestOnlyWithinTheSameConversation() {
    var attributes =
        Map.of(
            "permissionPolicyChannel",
            "web",
            "accountRole",
            "MEMBER",
            "conversationId",
            conversation,
            "zalava.runId",
            "run-1");
    var entry =
        new Entry(
            UUID.randomUUID().toString(),
            "2026-11-10T12:00:00Z",
            null,
            null,
            null,
            "shopping",
            "add_item",
            actor.accountId().toString(),
            attributes,
            Map.of(),
            List.of(),
            true,
            json.createObjectNode().put("secret", "hidden-argument"),
            "{}",
            null,
            Decision.DENIED,
            ApprovalScope.ONCE);
    new FileSystemApprovalRequestStore(workspace).save(entry);
    var runs = mock(AgentRunStore.class);
    var now = Instant.parse("2026-11-10T12:00:00Z");
    var run =
        new AgentRun(
            "run-1",
            conversation,
            AgentRun.PromptType.CONVERSATIONAL,
            "Add milk to the list",
            0,
            0,
            1000,
            0,
            List.of(),
            now,
            now,
            0,
            AgentRun.Status.SUCCEEDED,
            "",
            "");
    when(runs.recent()).thenReturn(List.of(run));
    String context =
        actors.call(
            actor,
            AccountRole.ADMIN,
            () ->
                ConversationChannelContext.call(
                    conversation,
                    () ->
                        new ChatApprovalContext(
                                requests(), actors, new ModelBoundary(64000, ""), runs)
                            .render()));
    assertThat(context)
        .contains(
            "approval denied", "originating user request (untrusted): \"Add milk to the list\"")
        .doesNotContain("hidden-argument");
    var foreign =
        new AgentRun(
            "run-1",
            "another-conversation",
            AgentRun.PromptType.CONVERSATIONAL,
            "private information",
            0,
            0,
            1000,
            0,
            List.of(),
            now,
            now,
            0,
            AgentRun.Status.SUCCEEDED,
            "",
            "");
    when(runs.recent()).thenReturn(List.of(foreign));
    String isolated =
        actors.call(
            actor,
            AccountRole.ADMIN,
            () ->
                ConversationChannelContext.call(
                    conversation,
                    () ->
                        new ChatApprovalContext(
                                requests(), actors, new ModelBoundary(64000, ""), runs)
                            .render()));
    assertThat(isolated).doesNotContain("private information");
  }

  @Test
  void reportsOnlyTheValidatedDockerSubjectAndRedactsKnownSecrets() {
    var attributes =
        Map.of(
            "permissionPolicyChannel",
            "web",
            "accountRole",
            "MEMBER",
            "conversationId",
            conversation,
            "approvedModuleId",
            "zalava-module-docker",
            "approvalExecutionOutcome",
            "succeeded");
    var arguments =
        json.createObjectNode()
            .put("container", "test-container")
            .put("secret", "sensitive-argument");
    var entry =
        new Entry(
            UUID.randomUUID().toString(),
            "2026-11-10T12:00:00Z",
            null,
            null,
            null,
            "docker-containers",
            "stopContainer",
            actor.accountId().toString(),
            attributes,
            Map.of(),
            List.of(),
            true,
            arguments,
            arguments.toString(),
            null,
            Decision.ALLOWED,
            ApprovalScope.ONCE);
    new FileSystemApprovalRequestStore(workspace).save(entry);
    assertThat(render(actor, conversation))
        .contains("container=test-container")
        .doesNotContain("sensitive-argument");
    String text =
        actors.call(
            actor,
            AccountRole.ADMIN,
            () ->
                ConversationChannelContext.call(
                    conversation,
                    () ->
                        new ChatApprovalContext(
                                requests(), actors, new ModelBoundary(64000, "test-container"))
                            .render()));
    assertThat(text)
        .contains("container=[REDACTED]", "approved; execution succeeded")
        .doesNotContain("sensitive-argument", "test-container");
  }

  @Test
  void onlyTheAuthenticatedActorAndExactConversationAreVisible() {
    save("visible", Decision.DENIED, Map.of(), true, actor, conversation);
    save(
        "different",
        Decision.DENIED,
        Map.of(),
        true,
        actor,
        new ActorConversationId(actor, ConversationReference.newReference()).value());
    save("other", Decision.DENIED, Map.of(), true, other, conversation);
    save(
        "channel",
        Decision.DENIED,
        Map.of("permissionPolicyChannel", "telegram"),
        true,
        actor,
        conversation);
    assertThat(render(actor, conversation))
        .contains("visible")
        .doesNotContain("different", "other", "channel");
    assertThat(render(other, conversation)).isEmpty();
    assertThat(render(actor, "bad-conversation")).isEmpty();
    assertThat(new ChatApprovalContext(requests(), actors).render()).isEmpty();
  }

  private String render(Actor principal, String id) {
    return actors.call(
        principal,
        AccountRole.ADMIN,
        () ->
            ConversationChannelContext.call(
                id, () -> new ChatApprovalContext(requests(), actors).render()));
  }

  private ZalavaToolApprovalRequests requests() {
    return new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
  }

  private void save(
      String tool,
      Decision decision,
      Map<String, String> extra,
      boolean sideEffecting,
      Actor owner,
      String id) {
    var attributes = new HashMap<String, String>();
    attributes.put("permissionPolicyChannel", "web");
    attributes.put("accountRole", "MEMBER");
    attributes.put("conversationId", id);
    attributes.putAll(extra);
    var entry =
        new Entry(
            UUID.randomUUID().toString(),
            "2026-11-10T12:00:00Z",
            null,
            null,
            null,
            "module",
            tool,
            owner.accountId().toString(),
            attributes,
            Map.of(),
            List.of(),
            sideEffecting,
            json.createObjectNode().put("secret", "sensitive-argument"),
            "{\"secret\":\"sensitive-argument\"}",
            null,
            decision,
            ApprovalScope.ONCE);
    new FileSystemApprovalRequestStore(workspace).save(entry);
  }
}
