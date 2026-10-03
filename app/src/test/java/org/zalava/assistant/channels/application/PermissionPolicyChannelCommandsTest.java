package org.zalava.assistant.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.channels.application.port.out.ChannelApprovalStore;
import org.zalava.assistant.channels.application.port.out.ChannelProviderOperations;
import org.zalava.assistant.channels.application.port.out.ChannelTasks;
import org.zalava.assistant.channels.domain.ChannelApproval;

class PermissionPolicyChannelCommandsTest {

  @Test
  void listsBoundedPolicyMetadataWithoutArguments() {
    RecordingPolicies policies =
        new RecordingPolicies(
            List.of(
                new ChannelApproval(
                    "policy-1", "files", "write", null, ChannelApproval.Decision.ALLOWED)));

    String response = commands(policies).handle("/zalava policies").orElseThrow();

    assertThat(response).contains("Zalava active durable tool policies", "files/write", "policy-1");
    assertThat(response).doesNotContain("arguments");
  }

  @Test
  void revokesPolicyByExactId() {
    RecordingPolicies policies =
        new RecordingPolicies(
            List.of(
                new ChannelApproval(
                    "policy-1", "files", "write", null, ChannelApproval.Decision.ALLOWED)));

    String response = commands(policies).handle("/zalava revoke-policy policy-1").orElseThrow();

    assertThat(policies.revokedRequestId).isEqualTo("policy-1");
    assertThat(response).contains("Zalava revoked durable policy files/write (policy-1)");
  }

  private static DefaultChannelApprovalCommands commands(ChannelApprovalStore policies) {
    return new DefaultChannelApprovalCommands(
        policies,
        new ChannelProviderOperations() {
          @Override
          public void allowUnscoped(String requestId) {}

          @Override
          public void allowUnscopedTool(String requestId) {}

          @Override
          public void denyUnscoped(String requestId) {}
        },
        new ChannelTasks() {
          @Override
          public boolean isAwaitingHumanInput(String taskReference) {
            return false;
          }

          @Override
          public void resume(String taskReference) {}
        });
  }

  private static final class RecordingPolicies implements ChannelApprovalStore {
    private final List<ChannelApproval> policies;
    private String revokedRequestId;

    private RecordingPolicies(List<ChannelApproval> policies) {
      this.policies = policies;
    }

    @Override
    public ChannelApproval get(String requestId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ChannelApproval> recent() {
      return List.of();
    }

    @Override
    public List<ChannelApproval> activeToolPolicies() {
      return policies;
    }

    @Override
    public ChannelApproval revokeToolPolicy(String requestId) {
      revokedRequestId = requestId;
      return policies.stream()
          .filter(policy -> policy.requestId().equals(requestId))
          .findFirst()
          .orElseThrow(() -> new DefaultChannelApprovalCommands.NotFoundException(requestId));
    }

    @Override
    public ChannelApproval allow(String requestId, String taskReference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChannelApproval allowTool(String requestId, String taskReference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChannelApproval deny(String requestId, String taskReference) {
      throw new UnsupportedOperationException();
    }
  }
}
