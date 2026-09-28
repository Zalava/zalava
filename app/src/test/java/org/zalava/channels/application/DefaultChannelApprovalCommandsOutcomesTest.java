package org.zalava.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.channels.application.port.out.ChannelApprovalStore;
import org.zalava.channels.application.port.out.ChannelProviderOperations;
import org.zalava.channels.application.port.out.ChannelTasks;
import org.zalava.channels.domain.ChannelApproval;

class DefaultChannelApprovalCommandsOutcomesTest {

  private static final ChannelProviderOperations RECORDING_OPERATIONS =
      new ChannelProviderOperations() {
        List<String> calls = new ArrayList<>();

        @Override
        public void allowUnscoped(String requestId) {
          calls.add("allow:" + requestId);
        }

        @Override
        public void allowUnscopedTool(String requestId) {
          calls.add("allowTool:" + requestId);
        }

        @Override
        public void denyUnscoped(String requestId) {
          calls.add("deny:" + requestId);
        }
      };

  @Test
  void nonApprovalMessagesAreIgnored() {
    DefaultChannelApprovalCommands commands = commands(new FakeApprovals(null), waiting(true));

    assertThat(commands.handle("hello there")).isEmpty();
    assertThat(commands.handle(null)).isEmpty();
    assertThat(commands.handle("  ")).isEmpty();
    // Bare /sea and wrong-arity commands are readable errors, not silence.
    assertThat(commands.handle("/sea")).contains("SEA could not understand that approval command.");
    assertThat(commands.handle("/SEA approve req extra"))
        .contains("SEA could not understand that approval command.");
    assertThat(commands.handle("sea approve req")).isEmpty();
  }

  @Test
  void malformedApprovalCommandsReturnAnError() {
    FakeApprovals approvals = new FakeApprovals(null);
    DefaultChannelApprovalCommands commands = commands(approvals, waiting(true));

    assertThat(commands.handle("/sea blink req-1"))
        .contains("SEA could not understand that approval command.");
    assertThat(commands.handle("/sea approve"))
        .contains("SEA could not understand that approval command.");
  }

  @Test
  void lastResolvesToTheMostRecentPendingRequest() {
    ChannelApproval pending =
        new ChannelApproval("req-1", "files", "write", null, ChannelApproval.Decision.PENDING);
    FakeApprovals approvals = new FakeApprovals(pending, List.of(pending));
    DefaultChannelApprovalCommands commands = commands(approvals, waiting(true));

    assertThat(commands.handle("/sea approve LAST"))
        .contains("Approval granted once for files/write. The approved tool has run.");
    assertThat(approvals.lastResolvedId).isEqualTo("req-1");
  }

  @Test
  void lastWithNoPendingRequestFailsGracefully() {
    FakeApprovals approvals = new FakeApprovals(null, List.of());
    DefaultChannelApprovalCommands commands = commands(approvals, waiting(true));

    assertThat(commands.handle("/sea deny last")).contains("SEA approval request not found: last");
  }

  @Test
  void unscopedApprovalsRouteThroughTheProviderOperations() {
    ChannelApproval unscoped =
        new ChannelApproval("req-2", "files", "write", null, ChannelApproval.Decision.PENDING);
    FakeApprovals approvals = new FakeApprovals(unscoped, List.of(unscoped));
    List<String> operations = new ArrayList<>();
    DefaultChannelApprovalCommands commands =
        new DefaultChannelApprovalCommands(
            approvals,
            new ChannelProviderOperations() {
              @Override
              public void allowUnscoped(String requestId) {
                operations.add("allow");
              }

              @Override
              public void allowUnscopedTool(String requestId) {
                operations.add("allowTool");
              }

              @Override
              public void denyUnscoped(String requestId) {
                operations.add("deny");
              }
            },
            waiting(true));

    assertThat(commands.handle("/sea approve req-2"))
        .contains("Approval granted once for files/write. The approved tool has run.");
    assertThat(commands.handle("/sea always-allow-tool req-2"))
        .contains("Tool approval policy saved for files/write. The approved tool has run.");
    assertThat(commands.handle("/sea deny req-2"))
        .contains("Approval denied for files/write. The tool was not run.");
    assertThat(operations).containsExactly("allow", "allowTool", "deny");
  }

  @Test
  void taskScopedApprovalsRefuseToDecideWhenTheJobIsNotWaiting() {
    ChannelApproval taskScoped =
        new ChannelApproval(
            "req-3", "files", "write", "2026-09-02/task.md", ChannelApproval.Decision.PENDING);
    FakeApprovals approvals = new FakeApprovals(taskScoped, List.of(taskScoped));
    DefaultChannelApprovalCommands commands = commands(approvals, waiting(false));

    assertThat(commands.handle("/sea deny req-3"))
        .contains("SEA cannot decide that approval because the job is not waiting for input.");
  }

  @Test
  void taskScopedDenyKeepsTheJobWaitingWhenOtherApprovalsRemainPending() {
    ChannelApproval pending =
        new ChannelApproval(
            "req-4", "files", "write", "2026-09-02/task.md", ChannelApproval.Decision.PENDING);
    FakeApprovals approvals =
        new FakeApprovals(pending, List.of(pending)) {
          @Override
          public List<ChannelApproval> recent() {
            // The denied entry plus another still-pending entry for the same job.
            return List.of(pending);
          }
        };
    List<String> resumed = new ArrayList<>();
    DefaultChannelApprovalCommands commands =
        new DefaultChannelApprovalCommands(
            approvals,
            RECORDING_OPERATIONS,
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

    assertThat(commands.handle("/sea deny req-4"))
        .contains(
            "Approval denied for files/write. The job is still waiting for another approval. Review: /jobs/2026-09-02/task.md");
    assertThat(resumed).isEmpty();
  }

  @Test
  void unknownRequestsAndConflictsAreReportedDeterministically() {
    FakeApprovals notFound =
        new FakeApprovals(null) {
          @Override
          public ChannelApproval get(String requestId) {
            throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
          }
        };
    assertThat(commands(notFound, waiting(true)).handle("/sea approve ghost"))
        .contains("SEA approval request not found: ghost");

    FakeApprovals decided =
        new FakeApprovals(pendingUnscoped()) {
          @Override
          public ChannelApproval get(String requestId) {
            throw new DefaultChannelApprovalCommands.AlreadyDecidedException(requestId);
          }
        };
    assertThat(commands(decided, waiting(true)).handle("/sea approve req-9"))
        .contains("SEA approval request was already decided: req-9");

    assertThat(commands(notFound, waiting(true)).handle("/sea approve ghost-last"))
        .contains("SEA approval request not found: ghost-last");
  }

  @Test
  void providerOperationFailuresMapToDeterministicResponses() {
    ChannelApproval unscoped = pendingUnscoped();
    FakeApprovals approvals = new FakeApprovals(unscoped, List.of(unscoped));
    DefaultChannelApprovalCommands commands =
        new DefaultChannelApprovalCommands(
            approvals,
            new ChannelProviderOperations() {
              @Override
              public void allowUnscoped(String requestId) {
                throw new org.zalava.channels.application.ChannelProviderOperationException(
                    org.zalava.channels.application.ChannelProviderOperationException.Code
                        .APPROVAL_NOT_FOUND,
                    "missing");
              }

              @Override
              public void allowUnscopedTool(String requestId) {
                throw new org.zalava.channels.application.ChannelProviderOperationException(
                    org.zalava.channels.application.ChannelProviderOperationException.Code
                        .APPROVAL_CONFLICT,
                    "conflict");
              }

              @Override
              public void denyUnscoped(String requestId) {
                throw new org.zalava.channels.application.ChannelProviderOperationException(
                    org.zalava.channels.application.ChannelProviderOperationException.Code.OTHER,
                    "backend exploded");
              }
            },
            waiting(true));

    assertThat(commands.handle("/sea approve req-1"))
        .contains("SEA approval request not found: req-1");
    assertThat(commands.handle("/sea always-allow-tool req-1"))
        .contains("SEA approval request was already decided: req-1");
    assertThat(commands.handle("/sea deny req-1"))
        .contains("SEA could not complete that approval command: backend exploded");
  }

  @Test
  void unexpectedFailuresReportACompletedFailureWithoutLeakingDetails() {
    ChannelApproval unscoped = pendingUnscoped();
    FakeApprovals approvals = new FakeApprovals(unscoped, List.of(unscoped));
    DefaultChannelApprovalCommands commands =
        new DefaultChannelApprovalCommands(
            approvals,
            new ChannelProviderOperations() {
              @Override
              public void allowUnscoped(String requestId) {
                throw new IllegalStateException("unexpected");
              }

              @Override
              public void allowUnscopedTool(String requestId) {}

              @Override
              public void denyUnscoped(String requestId) {}
            },
            waiting(true));

    assertThat(commands.handle("/sea approve req-1"))
        .contains("SEA could not complete that approval command: unexpected");
  }

  private static ChannelApproval pendingUnscoped() {
    return new ChannelApproval("req-1", "files", "write", null, ChannelApproval.Decision.PENDING);
  }

  private static ChannelTasks waiting(boolean isWaiting) {
    return new ChannelTasks() {
      @Override
      public boolean isAwaitingHumanInput(String reference) {
        return isWaiting;
      }

      @Override
      public void resume(String reference) {}
    };
  }

  private static DefaultChannelApprovalCommands commands(
      FakeApprovals approvals, ChannelTasks tasks) {
    return new DefaultChannelApprovalCommands(approvals, RECORDING_OPERATIONS, tasks);
  }

  private static class FakeApprovals implements ChannelApprovalStore {
    ChannelApproval current;
    private final List<ChannelApproval> recent;
    String lastResolvedId;

    FakeApprovals(ChannelApproval current) {
      this(current, null);
    }

    FakeApprovals(ChannelApproval current, List<ChannelApproval> recent) {
      this.current = current;
      this.recent = recent;
    }

    @Override
    public ChannelApproval get(String requestId) {
      if (current == null) throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
      lastResolvedId = requestId;
      return current;
    }

    @Override
    public List<ChannelApproval> recent() {
      return recent == null ? List.of(current) : recent;
    }

    @Override
    public ChannelApproval allow(String requestId, String reference) {
      return decided(ChannelApproval.Decision.ALLOWED);
    }

    @Override
    public ChannelApproval allowTool(String requestId, String reference) {
      return decided(ChannelApproval.Decision.ALLOWED);
    }

    @Override
    public ChannelApproval deny(String requestId, String reference) {
      return decided(ChannelApproval.Decision.DENIED);
    }

    ChannelApproval decided(ChannelApproval.Decision decision) {
      current =
          new ChannelApproval(
              current.requestId(),
              current.providerId(),
              current.toolName(),
              current.taskReference(),
              decision);
      return current;
    }
  }
}
