package org.zalava.channels.adapter.out.approval;

import java.util.List;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.channels.application.DefaultChannelApprovalCommands;
import org.zalava.channels.application.port.out.ChannelApprovalStore;
import org.zalava.channels.domain.ChannelApproval;
import org.zalava.tasks.domain.TaskReference;

public final class SeaChannelApprovalStore implements ChannelApprovalStore {
  private final SeaToolApprovalRequests requests;

  public SeaChannelApprovalStore(SeaToolApprovalRequests requests) {
    this.requests = requests;
  }

  @Override
  public ChannelApproval get(String requestId) {
    return map(getEntry(requestId));
  }

  @Override
  public List<ChannelApproval> recent() {
    return requests.recentEntries().stream().map(SeaChannelApprovalStore::map).toList();
  }

  @Override
  public List<ChannelApproval> activeToolPolicies() {
    return requests.activeToolPolicies().stream().map(SeaChannelApprovalStore::map).toList();
  }

  @Override
  public ChannelApproval revokeToolPolicy(String requestId) {
    try {
      return map(requests.revokeToolPolicy(requestId));
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
    }
  }

  @Override
  public ChannelApproval allow(String requestId, String taskReference) {
    return map(decide(() -> requests.allow(requestId, reference(taskReference)), requestId));
  }

  @Override
  public ChannelApproval allowTool(String requestId, String taskReference) {
    return map(decide(() -> requests.allowTool(requestId, reference(taskReference)), requestId));
  }

  @Override
  public ChannelApproval deny(String requestId, String taskReference) {
    return map(decide(() -> requests.deny(requestId, reference(taskReference)), requestId));
  }

  private SeaToolApprovalRequests.Entry getEntry(String requestId) {
    try {
      return requests.get(requestId);
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
    }
  }

  private SeaToolApprovalRequests.Entry decide(EntrySupplier action, String requestId) {
    try {
      return action.get();
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException ex) {
      throw new DefaultChannelApprovalCommands.AlreadyDecidedException(requestId);
    }
  }

  private static TaskReference reference(String value) {
    String[] parts = value.split("/", 2);
    if (parts.length != 2) throw new IllegalArgumentException("Invalid task reference");
    return TaskReference.parse(parts[0], parts[1]);
  }

  private static ChannelApproval map(SeaToolApprovalRequests.Entry entry) {
    return new ChannelApproval(
        entry.requestId(),
        entry.providerId(),
        entry.toolName(),
        entry.taskReference(),
        switch (entry.decision()) {
          case PENDING -> ChannelApproval.Decision.PENDING;
          case ALLOWED -> ChannelApproval.Decision.ALLOWED;
          case DENIED -> ChannelApproval.Decision.DENIED;
          case REVOKED -> ChannelApproval.Decision.REVOKED;
        });
  }

  @FunctionalInterface
  private interface EntrySupplier {
    SeaToolApprovalRequests.Entry get();
  }
}
