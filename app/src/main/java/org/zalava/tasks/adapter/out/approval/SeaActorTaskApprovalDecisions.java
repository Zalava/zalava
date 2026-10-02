package org.zalava.tasks.adapter.out.approval;

import java.util.List;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.domain.ActorTaskReference;

/** Owner-scoped approval view for actor task execution. */
public final class SeaActorTaskApprovalDecisions implements ActorTaskApprovalDecisions {
  private final SeaToolApprovalRequests approvals;

  public SeaActorTaskApprovalDecisions(SeaToolApprovalRequests approvals) {
    this.approvals = approvals;
  }

  @Override
  public boolean hasPending(Actor actor, ActorTaskReference taskReference) {
    return approvals.hasPending(actor, taskReference);
  }

  @Override
  public List<PendingApproval> pendingFor(Actor actor, ActorTaskReference taskReference) {
    return approvals.pendingFor(actor, taskReference).stream()
        .map(
            entry ->
                new PendingApproval(
                    entry.requestId(),
                    entry.providerId(),
                    entry.toolName(),
                    entry.summary().prompt(),
                    entry.summary().effect(),
                    entry.summary().argumentsPreview(),
                    entry.summary().scope(),
                    entry.summary().policyTags()))
        .toList();
  }

  @Override
  public List<Decision> unconsumedFor(Actor actor, ActorTaskReference taskReference) {
    return approvals.unconsumedDecisionsFor(actor, taskReference).stream()
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
