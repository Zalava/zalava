package org.zalava.assistant.channels.adapter.out.approval;

import java.util.List;
import org.zalava.assistant.channels.application.DefaultChannelApprovalCommands;
import org.zalava.assistant.channels.application.port.out.ChannelApprovalStore;
import org.zalava.assistant.channels.domain.ChannelApproval;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.tasks.domain.TaskReference;

public final class ZalavaChannelApprovalStore implements ChannelApprovalStore {
  private final ZalavaToolApprovalRequests requests;

  public ZalavaChannelApprovalStore(ZalavaToolApprovalRequests requests) {
    this.requests = requests;
  }

  @Override
  public ChannelApproval get(String requestId) {
    return map(getEntry(requestId));
  }

  @Override
  public List<ChannelApproval> recent() {
    return requests.recentEntries().stream().map(ZalavaChannelApprovalStore::map).toList();
  }

  @Override
  public List<ChannelApproval> activeToolPolicies() {
    return requests.activeToolPolicies().stream().map(ZalavaChannelApprovalStore::map).toList();
  }

  @Override
  public ChannelApproval revokeToolPolicy(String requestId) {
    try {
      return map(requests.revokeToolPolicy(requestId));
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
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

  private ZalavaToolApprovalRequests.Entry getEntry(String requestId) {
    try {
      return requests.get(requestId);
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
    }
  }

  private ZalavaToolApprovalRequests.Entry decide(EntrySupplier action, String requestId) {
    try {
      return action.get();
    } catch (ZalavaToolApprovalRequests.NotFoundException ex) {
      throw new DefaultChannelApprovalCommands.NotFoundException(requestId);
    } catch (ZalavaToolApprovalRequests.AlreadyDecidedException ex) {
      throw new DefaultChannelApprovalCommands.AlreadyDecidedException(requestId);
    }
  }

  private static TaskReference reference(String value) {
    String[] parts = value.split("/", 2);
    if (parts.length != 2) throw new IllegalArgumentException("Invalid task reference");
    return TaskReference.parse(parts[0], parts[1]);
  }

  private static ChannelApproval map(ZalavaToolApprovalRequests.Entry entry) {
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
    ZalavaToolApprovalRequests.Entry get();
  }
}
