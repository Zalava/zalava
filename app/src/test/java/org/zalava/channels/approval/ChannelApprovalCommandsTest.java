package org.zalava.channels.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.operation.application.model.ToolApproval;
import org.zalava.operation.application.port.in.ProviderToolOperations;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("deprecation")
class ChannelApprovalCommandsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void ignoresMessagesThatAreNotSeaCommands() {
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(
            new SeaToolApprovalRequests(),
            mock(ProviderToolOperations.class),
            mock(TaskCommands.class),
            mock(TaskQueries.class));

    assertThat(commands.handle("hello")).isEmpty();
  }

  @Test
  void allowsPendingApprovalAndResumesTask() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    String requestId = createApproval(approvals, reference);
    when(taskQueries.getTask(reference)).thenReturn(task(reference));

    assertThat(commands.handle("/sea approve " + requestId))
        .get()
        .asString()
        .contains("Approval granted once")
        .contains("filesystem-workspace/writeFile")
        .contains("The job has been queued to continue.")
        .contains("/jobs/2026-06-08/120000-write-file.md");

    assertThat(approvals.get(requestId).decision())
        .isEqualTo(SeaToolApprovalRequests.Decision.ALLOWED);
    verify(taskCommands).resume(reference);
  }

  @Test
  void savesToolPolicyWithoutResumingWhenAnotherApprovalIsPending() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    String firstRequestId = createApproval(approvals, reference);
    createApproval(approvals, reference, "notes/b.txt");
    when(taskQueries.getTask(reference)).thenReturn(task(reference));

    assertThat(commands.handle("/sea always-allow-tool " + firstRequestId))
        .get()
        .asString()
        .contains("Tool approval policy saved")
        .contains("The job is still waiting for another approval.");

    assertThat(approvals.get(firstRequestId).approvalScope())
        .isEqualTo(SeaToolApprovalRequests.ApprovalScope.TOOL);
    verify(taskCommands, never()).resume(reference);
  }

  @Test
  void deniesPendingApprovalAndResumesTask() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    String requestId = createApproval(approvals, reference);
    when(taskQueries.getTask(reference)).thenReturn(task(reference));

    assertThat(commands.handle("/sea deny " + requestId))
        .get()
        .asString()
        .contains("Approval denied")
        .contains("The job has been queued to continue.");

    assertThat(approvals.get(requestId).decision())
        .isEqualTo(SeaToolApprovalRequests.Decision.DENIED);
    verify(taskCommands).resume(reference);
  }

  @Test
  void rejectsMalformedSeaCommandWithoutCallingTaskLayer() {
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(
            new SeaToolApprovalRequests(),
            mock(ProviderToolOperations.class),
            taskCommands,
            taskQueries);

    assertThat(commands.handle("/sea approve"))
        .get()
        .asString()
        .contains("SEA could not understand that approval command.");
    verify(taskCommands, never()).resume(org.mockito.Mockito.any());
    verify(taskQueries, never()).getTask(org.mockito.Mockito.any());
  }

  @Test
  void allowsUnscopedApprovalWithoutTaskLookup() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    String requestId = createUnscopedApproval(approvals);
    when(providerToolOperations.allowUnscoped(requestId))
        .thenReturn(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                ZalavaOperationResult.success(Map.of())));

    assertThat(commands.handle("/sea approve " + requestId))
        .get()
        .asString()
        .contains("Approval granted once")
        .contains("filesystem-workspace/writeFile")
        .contains("The approved tool has run.");

    verify(providerToolOperations).allowUnscoped(requestId);
    verify(taskCommands, never()).resume(org.mockito.Mockito.any());
    verify(taskQueries, never()).getTask(org.mockito.Mockito.any());
  }

  @Test
  void savesUnscopedToolPolicyWithoutTaskLookup() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    String requestId = createUnscopedApproval(approvals);
    when(providerToolOperations.allowUnscopedTool(requestId))
        .thenReturn(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                ZalavaOperationResult.success(Map.of())));

    assertThat(commands.handle("/sea always-allow-tool " + requestId))
        .get()
        .asString()
        .contains("Tool approval policy saved")
        .contains("filesystem-workspace/writeFile")
        .contains("The approved tool has run.");

    verify(providerToolOperations).allowUnscopedTool(requestId);
    verify(taskCommands, never()).resume(org.mockito.Mockito.any());
    verify(taskQueries, never()).getTask(org.mockito.Mockito.any());
  }

  @Test
  void deniesUnscopedApprovalWithoutTaskLookup() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    String requestId = createUnscopedApproval(approvals);
    when(providerToolOperations.denyUnscoped(requestId))
        .thenReturn(toolApproval(requestId, ToolApproval.Decision.DENIED));

    assertThat(commands.handle("/sea deny " + requestId))
        .get()
        .asString()
        .contains("Approval denied")
        .contains("filesystem-workspace/writeFile")
        .contains("The tool was not run.");

    verify(providerToolOperations).denyUnscoped(requestId);
    verify(taskCommands, never()).resume(org.mockito.Mockito.any());
    verify(taskQueries, never()).getTask(org.mockito.Mockito.any());
  }

  @Test
  void resolvesLastToNewestPendingApproval() throws Exception {
    SeaToolApprovalRequests approvals = new SeaToolApprovalRequests();
    ProviderToolOperations providerToolOperations = mock(ProviderToolOperations.class);
    TaskCommands taskCommands = mock(TaskCommands.class);
    TaskQueries taskQueries = mock(TaskQueries.class);
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(approvals, providerToolOperations, taskCommands, taskQueries);
    createApproval(approvals, null, "notes/old.txt");
    String newestRequestId = createApproval(approvals, null, "notes/new.txt");
    when(providerToolOperations.allowUnscoped(newestRequestId))
        .thenReturn(
            ProviderToolOperations.ToolInvocationOutcome.executed(
                ZalavaOperationResult.success(Map.of())));

    assertThat(commands.handle("/sea approve last"))
        .get()
        .asString()
        .contains("Approval granted once")
        .contains("filesystem-workspace/writeFile");

    verify(providerToolOperations).allowUnscoped(newestRequestId);
  }

  @Test
  void reportsWhenLastHasNoPendingApproval() {
    ChannelApprovalCommands commands =
        new ChannelApprovalCommands(
            new SeaToolApprovalRequests(),
            mock(ProviderToolOperations.class),
            mock(TaskCommands.class),
            mock(TaskQueries.class));

    assertThat(commands.handle("/sea approve last"))
        .get()
        .asString()
        .contains("SEA approval request not found: last");
  }

  private static String createApproval(SeaToolApprovalRequests approvals, TaskReference reference)
      throws Exception {
    return createApproval(approvals, reference, "notes/a.txt");
  }

  private static String createUnscopedApproval(SeaToolApprovalRequests approvals) throws Exception {
    return createApproval(approvals, null, "notes/a.txt");
  }

  private static String createApproval(
      SeaToolApprovalRequests approvals, TaskReference reference, String path) throws Exception {
    return approvals
        .create(
            new TestProvider(),
            new ZalavaToolDescriptor(
                "writeFile", "Writes a file.", true, List.of("filesystem.write")),
            new InvocationContext("telegram-42", false, Map.of("source", "telegram")),
            JSON.readTree("{\"path\":\"" + path + "\"}"),
            reference)
        .requestId();
  }

  private static ToolApproval toolApproval(String requestId, ToolApproval.Decision decision) {
    return new ToolApproval(
        requestId,
        "filesystem-workspace",
        "writeFile",
        "agent",
        Map.of("source", "telegram"),
        Map.of("root", "workspace"),
        "{\"path\":\"notes/a.txt\"}",
        null,
        decision,
        ToolApproval.ApprovalScope.ONCE);
  }

  private static Task task(TaskReference reference) {
    return new Task(
        "/workspace/tasks/" + reference.path(),
        "Write file",
        Instant.now(),
        Task.Status.awaiting_human_input,
        "Write a file.");
  }

  private static final class TestProvider implements ZalavaProvider {

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          "filesystem-workspace",
          "filesystem",
          "filesystem",
          "Workspace Files",
          "Workspace filesystem provider.",
          "1.0.0",
          capabilities(),
          List.of("filesystem"),
          Map.of("root", "workspace"));
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(new ZalavaToolDescriptor("writeFile", "Writes a file.", true, null));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, tools.jackson.databind.JsonNode arguments, InvocationContext context) {
      return ZalavaOperationResult.success(Map.of());
    }
  }
}
