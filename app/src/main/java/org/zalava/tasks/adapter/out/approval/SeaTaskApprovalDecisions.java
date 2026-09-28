package org.zalava.tasks.adapter.out.approval;

import java.util.List;
import org.springframework.stereotype.Component;
import org.zalava.approval.SeaToolApprovalRequests;
import org.zalava.tasks.application.port.out.TaskApprovalDecisions;
import org.zalava.tasks.domain.TaskReference;

@Component
public class SeaTaskApprovalDecisions implements TaskApprovalDecisions {

  private final SeaToolApprovalRequests approvalRequests;

  public SeaTaskApprovalDecisions(SeaToolApprovalRequests approvalRequests) {
    this.approvalRequests = approvalRequests;
  }

  @Override
  public boolean hasPending(TaskReference taskReference) {
    return approvalRequests.hasPending(taskReference);
  }

  @Override
  public List<PendingApproval> pendingFor(TaskReference taskReference) {
    return approvalRequests.pendingFor(taskReference).stream()
        .map(
            entry ->
                new PendingApproval(
                    entry.requestId(),
                    entry.providerId(),
                    entry.toolName(),
                    entry.actorId(),
                    entry.summary().prompt(),
                    entry.summary().effect(),
                    entry.summary().argumentsPreview(),
                    "/sea approve " + entry.requestId(),
                    "/sea always-allow-tool " + entry.requestId(),
                    "/sea deny " + entry.requestId(),
                    entry.summary().scope(),
                    entry.summary().policyTags()))
        .toList();
  }

  @Override
  public List<Decision> unconsumedFor(TaskReference taskReference) {
    return approvalRequests.unconsumedDecisionsFor(taskReference).stream()
        .map(
            entry ->
                new Decision(
                    entry.decision().name(),
                    entry.providerId(),
                    entry.toolName(),
                    entry.argumentsJson()))
        .toList();
  }
}
