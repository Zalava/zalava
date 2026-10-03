package org.zalava.assistant.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.channels.application.port.out.ChannelApprovalStore;
import org.zalava.assistant.channels.application.port.out.ChannelProviderOperations;
import org.zalava.assistant.channels.application.port.out.ChannelTasks;
import org.zalava.assistant.channels.domain.ChannelApproval;

class DefaultChannelApprovalCommandsTest {

  @Test
  void decidesTaskScopedApprovalAndResumesWhenNoPendingApprovalRemains() {
    ChannelApproval pending =
        new ChannelApproval(
            "request-1", "files", "write", "2026-08-07/task.md", ChannelApproval.Decision.PENDING);
    FakeApprovals approvals = new FakeApprovals(pending);
    List<String> resumed = new ArrayList<>();
    DefaultChannelApprovalCommands commands =
        new DefaultChannelApprovalCommands(
            approvals,
            noUnscopedOperations(),
            new ChannelTasks() {
              @Override
              public boolean isAwaitingHumanInput(String reference) {
                return true;
              }

              @Override
              public void resume(String reference) {
                resumed.add(reference);
              }
            });

    assertThat(commands.handle("/zalava approve request-1"))
        .contains(
            "Approval granted once for files/write. The job has been queued to continue. Review: /jobs/2026-08-07/task.md");
    assertThat(resumed).containsExactly("2026-08-07/task.md");
  }

  private static ChannelProviderOperations noUnscopedOperations() {
    return new ChannelProviderOperations() {
      @Override
      public void allowUnscoped(String requestId) {}

      @Override
      public void allowUnscopedTool(String requestId) {}

      @Override
      public void denyUnscoped(String requestId) {}
    };
  }

  private static final class FakeApprovals implements ChannelApprovalStore {
    private ChannelApproval approval;

    private FakeApprovals(ChannelApproval approval) {
      this.approval = approval;
    }

    @Override
    public ChannelApproval get(String requestId) {
      return approval;
    }

    @Override
    public List<ChannelApproval> recent() {
      return List.of(approval);
    }

    @Override
    public ChannelApproval allow(String requestId, String reference) {
      return approval = decided(ChannelApproval.Decision.ALLOWED);
    }

    @Override
    public ChannelApproval allowTool(String requestId, String reference) {
      return approval = decided(ChannelApproval.Decision.ALLOWED);
    }

    @Override
    public ChannelApproval deny(String requestId, String reference) {
      return approval = decided(ChannelApproval.Decision.DENIED);
    }

    private ChannelApproval decided(ChannelApproval.Decision decision) {
      return new ChannelApproval(
          approval.requestId(),
          approval.providerId(),
          approval.toolName(),
          approval.taskReference(),
          decision);
    }
  }
}
