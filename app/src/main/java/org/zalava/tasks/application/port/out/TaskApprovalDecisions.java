package org.zalava.tasks.application.port.out;

import java.util.List;
import java.util.Map;
import org.zalava.tasks.domain.TaskReference;

public interface TaskApprovalDecisions {

  boolean hasPending(TaskReference taskReference);

  List<PendingApproval> pendingFor(TaskReference taskReference);

  List<Decision> unconsumedFor(TaskReference taskReference);

  record Decision(String decision, String providerId, String toolName, String argumentsJson) {}

  record PendingApproval(
      String requestId,
      String providerId,
      String toolName,
      String actorId,
      String prompt,
      String effect,
      String argumentsPreview,
      String allowOnceCommand,
      String allowToolCommand,
      String denyCommand,
      Map<String, String> scope,
      List<String> policyTags) {
    public PendingApproval {
      scope = scope == null ? Map.of() : Map.copyOf(scope);
      policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    }
  }
}
