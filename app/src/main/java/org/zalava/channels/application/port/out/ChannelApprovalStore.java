package org.zalava.channels.application.port.out;

import java.util.List;
import org.zalava.channels.domain.ChannelApproval;

public interface ChannelApprovalStore {
  ChannelApproval get(String requestId);

  List<ChannelApproval> recent();

  default List<ChannelApproval> activeToolPolicies() {
    return List.of();
  }

  default ChannelApproval revokeToolPolicy(String requestId) {
    throw new UnsupportedOperationException("Durable policy revocation is unavailable");
  }

  ChannelApproval allow(String requestId, String taskReference);

  ChannelApproval allowTool(String requestId, String taskReference);

  ChannelApproval deny(String requestId, String taskReference);
}
