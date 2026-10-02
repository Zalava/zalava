package org.zalava.tasks.adapter.out.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.capabilities.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.ObjectMapper;

class SeaTaskApprovalDecisionsTest {

  @TempDir Path workspace;

  private SeaToolApprovalRequests requests;

  @BeforeEach
  void setUp() {
    requests = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
  }

  private TaskReference taskReference(String name) {
    return new TaskReference(java.time.LocalDate.of(2026, 9, 2), name);
  }

  private SeaToolApprovalRequests.Entry createTaskScopedEntry(TaskReference reference) {
    return requests.create(
        provider("files"),
        tool("write"),
        new org.zalava.InvocationContext("member-1", false, Map.of()),
        new ObjectMapper().createObjectNode().put("path", "report.md"),
        reference);
  }

  private org.zalava.ZalavaProvider provider(String id) {
    return new org.zalava.ZalavaProvider() {
      @Override
      public org.zalava.ProviderDescriptor descriptor() {
        return new org.zalava.ProviderDescriptor(
            id,
            "module",
            "tool",
            id,
            id,
            "1.0.0",
            org.zalava.ProviderCapabilities.toolsOnly(),
            List.of(),
            Map.of());
      }

      @Override
      public org.zalava.ProviderCapabilities capabilities() {
        return org.zalava.ProviderCapabilities.toolsOnly();
      }

      @Override
      public List<org.zalava.ZalavaToolDescriptor> listTools() {
        return List.of();
      }
    };
  }

  private org.zalava.ZalavaToolDescriptor tool(String name) {
    return new org.zalava.ZalavaToolDescriptor(
        name, "A write tool", true, List.of("files:write"), Map.of());
  }

  @Test
  void exposesPendingAndDecidedTaskScopedApprovals() {
    TaskReference reference = taskReference("120000-approvals.md");
    SeaToolApprovalRequests.Entry entry = createTaskScopedEntry(reference);

    SeaTaskApprovalDecisions decisions = new SeaTaskApprovalDecisions(requests);
    assertThat(decisions.hasPending(reference)).isTrue();
    List<SeaTaskApprovalDecisions.PendingApproval> pending = decisions.pendingFor(reference);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().requestId()).isEqualTo(entry.requestId());
    assertThat(pending.getFirst().prompt()).contains("files");
    assertThat(pending.getFirst().allowOnceCommand())
        .isEqualTo("/sea approve " + entry.requestId());
    assertThat(pending.getFirst().allowToolCommand())
        .isEqualTo("/sea always-allow-tool " + entry.requestId());
    assertThat(pending.getFirst().denyCommand()).isEqualTo("/sea deny " + entry.requestId());
    assertThat(pending.getFirst().policyTags()).containsExactly("files:write");

    requests.allow(entry.requestId(), reference);
    assertThat(decisions.hasPending(reference)).isFalse();
    List<SeaTaskApprovalDecisions.Decision> unconsumed = decisions.unconsumedFor(reference);
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

    SeaToolApprovalRequests.Entry entry =
        requests.create(
            provider("browser"),
            tool("open"),
            new org.zalava.InvocationContext(
                owner.accountId().toString(),
                false,
                Map.of(SeaToolApprovalRequests.ACTOR_TASK_REFERENCE, encoded)),
            new ObjectMapper().createObjectNode().put("url", "https://example.com"));

    SeaActorTaskApprovalDecisions decisions = new SeaActorTaskApprovalDecisions(requests);
    assertThat(decisions.hasPending(owner, taskReference)).isTrue();
    assertThat(decisions.hasPending(other, taskReference)).isFalse();
    List<SeaActorTaskApprovalDecisions.PendingApproval> pending =
        decisions.pendingFor(owner, taskReference);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().requestId()).isEqualTo(entry.requestId());
    assertThat(pending.getFirst().prompt()).contains("browser");
    assertThat(pending.getFirst().scope()).isEmpty();

    requests.allowUnscoped(entry.requestId());
    List<SeaActorTaskApprovalDecisions.Decision> unconsumed =
        decisions.unconsumedFor(owner, taskReference);
    assertThat(unconsumed).hasSize(1);
    assertThat(unconsumed.getFirst().decision()).isEqualTo("ALLOWED");
  }

  @Test
  void pendingViewsStayEmptyWithoutMatchingApprovals() {
    TaskReference reference = taskReference("130000-empty.md");

    SeaTaskApprovalDecisions decisions = new SeaTaskApprovalDecisions(requests);
    SeaActorTaskApprovalDecisions actorDecisions = new SeaActorTaskApprovalDecisions(requests);
    Actor actor = new Actor(AccountId.newId());

    assertThat(decisions.hasPending(reference)).isFalse();
    assertThat(decisions.pendingFor(reference)).isEmpty();
    assertThat(decisions.unconsumedFor(reference)).isEmpty();
    assertThat(actorDecisions.hasPending(actor, ActorTaskReference.newReference())).isFalse();
    assertThat(actorDecisions.pendingFor(actor, ActorTaskReference.newReference())).isEmpty();
    assertThat(actorDecisions.unconsumedFor(actor, ActorTaskReference.newReference())).isEmpty();
  }
}
