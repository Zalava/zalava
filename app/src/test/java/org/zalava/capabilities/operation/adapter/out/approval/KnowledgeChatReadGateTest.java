package org.zalava.capabilities.operation.adapter.out.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.ConversationChannelContext;
import org.zalava.assistant.agent.application.KnowledgeContextEnrichment;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.adapter.in.agent.KnowledgeAgentTools;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.KnowledgeToolObservation;
import org.zalava.knowledge.application.port.out.KnowledgeEvidenceStore;
import org.zalava.tasks.domain.TaskExecutionContext;

class KnowledgeChatReadGateTest {
  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final TaskExecutionContext tasks = new TaskExecutionContext();
  private final ChatToolConfirmation confirmation = mock(ChatToolConfirmation.class);
  private final ZalavaToolApprovalRequests approvals = new ZalavaToolApprovalRequests();
  private final KnowledgeEvidenceStore store = mock(KnowledgeEvidenceStore.class);
  private final Actor member = new Actor(new AccountId(UUID.randomUUID()));
  private final Actor other = new Actor(new AccountId(UUID.randomUUID()));
  private final KnowledgeAgentTools tools =
      new KnowledgeAgentTools(
          new KnowledgeEvidenceQueries(store),
          actors,
          new ModelBoundary(30000, ""),
          new KnowledgeToolObservation(List.of()),
          new KnowledgeChatReadGate(actors, tasks, confirmation, approvals));

  @Test
  void approvalIsOneUseAndBoundToPersonConversationAndArguments() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(true);
    assertThat(call(member, "chat-a", () -> tools.search("warranty", 2)))
        .contains("pending_approval");
    verifyNoInteractions(store);
    var original = approvals.recentEntries().getFirst();
    approvals.approveChatRead(original.requestId());
    assertThat(call(other, "chat-a", () -> tools.search("warranty", 2)))
        .contains("pending_approval");
    assertThat(call(member, "chat-b", () -> tools.search("warranty", 2)))
        .contains("pending_approval");
    assertThat(call(member, "chat-a", () -> tools.search("other", 2))).contains("pending_approval");
    verifyNoInteractions(store);
    assertThat(call(member, "chat-a", () -> tools.search("warranty", 2))).contains("NO_MATCH");
    verify(store).search(member, "warranty", 2);
    assertThat(call(member, "chat-a", () -> tools.search("warranty", 2)))
        .contains("pending_approval");
    verifyNoMoreInteractions(store);
  }

  @Test
  void revokedAuthorityCannotConsumeApproval() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    assertThat(call(member, "chat-a", () -> tools.search("private", null)))
        .contains("pending_approval");
    approvals.approveChatRead(approvals.recentEntries().getFirst().requestId());
    when(confirmation.mayConsumeReadApproval(any())).thenReturn(false);
    assertThat(call(member, "chat-a", () -> tools.search("private", null)))
        .contains("pending_approval");
    verifyNoInteractions(store);
  }

  @Test
  void absentConfirmationPreservesActorScopedReads() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.empty());
    assertThat(call(member, "chat-a", () -> tools.search("warranty", null))).contains("NO_MATCH");
    verify(store).search(member, "warranty", 5);
    assertThat(approvals.recentEntries()).isEmpty();
  }

  @Test
  void confirmationDisablesAutomaticKnowledgeEnrichment() {
    when(confirmation.requireFor(any(), any(), any())).thenReturn(Optional.of(true));
    var gate = new KnowledgeChatReadGate(actors, tasks, confirmation, approvals);
    var enrichment =
        new KnowledgeContextEnrichment(
            new KnowledgeEvidenceQueries(store), actors, new ModelBoundary(30000, ""), true, gate);
    var selected =
        actors.call(
            member,
            AccountRole.MEMBER,
            () -> ConversationChannelContext.call("chat-a", () -> enrichment.select("private")));
    assertThat(selected).isEmpty();
    verifyNoInteractions(store);
    assertThat(approvals.recentEntries()).isEmpty();
  }

  private String call(Actor actor, String conversation, Supplier<String> action) {
    return actors.call(
        actor, AccountRole.MEMBER, () -> ConversationChannelContext.call(conversation, action));
  }
}
