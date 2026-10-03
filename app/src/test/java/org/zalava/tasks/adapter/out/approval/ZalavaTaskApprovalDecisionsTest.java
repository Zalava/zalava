package org.zalava.tasks.adapter.out.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.ObjectMapper;

class ZalavaTaskApprovalDecisionsTest {

  @TempDir Path workspace;

  private ZalavaToolApprovalRequests requests;

  @BeforeEach
  void setUp() {
    requests = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
  }

  private TaskReference taskReference(String name) {
    return new TaskReference(java.time.LocalDate.of(2026, 9, 2), name);
  }

  private ZalavaToolApprovalRequests.Entry createTaskScopedEntry(TaskReference reference) {
    return requests.create(
        provider("files"),
        tool("write"),
        new org.zalava.api.InvocationContext("member-1", false, Map.of()),
        new ObjectMapper().createObjectNode().put("path", "report.md"),
        reference);
  }

  private org.zalava.api.ZalavaProvider provider(String id) {
    return new org.zalava.api.ZalavaProvider() {
      @Override
      public org.zalava.api.ProviderDescriptor descriptor() {
        return new org.zalava.api.ProviderDescriptor(
            id,
            "module",
            "tool",
            id,
            id,
            "1.0.0",
            org.zalava.api.ProviderCapabilities.toolsOnly(),
            List.of(),
            Map.of());
      }

      @Override
      public org.zalava.api.ProviderCapabilities capabilities() {
        return org.zalava.api.ProviderCapabilities.toolsOnly();
      }

      @Override
      public List<org.zalava.api.ZalavaToolDescriptor> listTools() {
        return List.of();
      }
    };
  }

  private org.zalava.api.ZalavaToolDescriptor tool(String name) {
    return new org.zalava.api.ZalavaToolDescriptor(
        name, "A write tool", true, List.of("files:write"), Map.of());
  }

  @Test
  void exposesPendingAndDecidedTaskScopedApprovals() {
    TaskReference reference = taskReference("120000-approvals.md");
    ZalavaToolApprovalRequests.Entry entry = createTaskScopedEntry(reference);

    ZalavaTaskApprovalDecisions decisions = new ZalavaTaskApprovalDecisions(requests);
    assertThat(decisions.hasPending(reference)).isTrue();
    List<ZalavaTaskApprovalDecisions.PendingApproval> pending = decisions.pendingFor(reference);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().requestId()).isEqualTo(entry.requestId());
    assertThat(pending.getFirst().prompt()).contains("files");
    assertThat(pending.getFirst().allowOnceCommand())
        .isEqualTo("/zalava approve " + entry.requestId());
    assertThat(pending.getFirst().allowToolCommand())
        .isEqualTo("/zalava always-allow-tool " + entry.requestId());
    assertThat(pending.getFirst().denyCommand()).isEqualTo("/zalava deny " + entry.requestId());
    assertThat(pending.getFirst().policyTags()).containsExactly("files:write");

    requests.allow(entry.requestId(), reference);
    assertThat(decisions.hasPending(reference)).isFalse();
    List<ZalavaTaskApprovalDecisions.Decision> unconsumed = decisions.unconsumedFor(reference);
    assertThat(unconsumed).hasSize(1);
    assertThat(unconsumed.getFirst().decision()).isEqualTo("ALLOWED");
    assertThat(unconsumed.getFirst().providerId()).isEqualTo("files");
    assertThat(unconsumed.getFirst().toolName()).isEqualTo("write");
    assertThat(unconsumed.getFirst().argumentsJson()).contains("report.md");
  }

  @Test
  void actorScopedViewsAreOwnerBoundAndOnlyExposeActorTaskEntries() {
    Actor owner = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    ActorTaskReference taskReference = ActorTaskReference.newReference();
    String encoded = new ActorTaskExecutionReference(owner, taskReference).encode();

    ZalavaToolApprovalRequests.Entry entry =
        requests.create(
            provider("browser"),
            tool("open"),
            new org.zalava.api.InvocationContext(
                owner.accountId().toString(),
                false,
                Map.of(ZalavaToolApprovalRequests.ACTOR_TASK_REFERENCE, encoded)),
            new ObjectMapper().createObjectNode().put("url", "https://example.com"));

    ZalavaActorTaskApprovalDecisions decisions = new ZalavaActorTaskApprovalDecisions(requests);
    assertThat(decisions.hasPending(owner, taskReference)).isTrue();
    assertThat(decisions.hasPending(other, taskReference)).isFalse();
    List<ZalavaActorTaskApprovalDecisions.PendingApproval> pending =
        decisions.pendingFor(owner, taskReference);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().requestId()).isEqualTo(entry.requestId());
    assertThat(pending.getFirst().prompt()).contains("browser");
    assertThat(pending.getFirst().scope()).isEmpty();

    requests.allowUnscoped(entry.requestId());
    List<ZalavaActorTaskApprovalDecisions.Decision> unconsumed =
        decisions.unconsumedFor(owner, taskReference);
    assertThat(unconsumed).hasSize(1);
    assertThat(unconsumed.getFirst().decision()).isEqualTo("ALLOWED");
  }

  @Test
  void pendingViewsStayEmptyWithoutMatchingApprovals() {
    TaskReference reference = taskReference("130000-empty.md");

    ZalavaTaskApprovalDecisions decisions = new ZalavaTaskApprovalDecisions(requests);
    ZalavaActorTaskApprovalDecisions actorDecisions =
        new ZalavaActorTaskApprovalDecisions(requests);
    Actor actor = new Actor(AccountId.newId());

    assertThat(decisions.hasPending(reference)).isFalse();
    assertThat(decisions.pendingFor(reference)).isEmpty();
    assertThat(decisions.unconsumedFor(reference)).isEmpty();
    assertThat(actorDecisions.hasPending(actor, ActorTaskReference.newReference())).isFalse();
    assertThat(actorDecisions.pendingFor(actor, ActorTaskReference.newReference())).isEmpty();
    assertThat(actorDecisions.unconsumedFor(actor, ActorTaskReference.newReference())).isEmpty();
  }
}
