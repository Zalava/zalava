package org.zalava.capabilities.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.RemoteModuleCandidate;

class RemoteCandidatePolicyTest {

  private final RemoteCandidatePolicy policy = new RemoteCandidatePolicy();

  @Test
  void rejectsCandidatesDeclaringBlockedPermissions() {
    assertThat(policy.eligible(candidate(List.of("shell")))).isFalse();
    assertThat(policy.eligible(candidate(List.of("unrestricted-host")))).isFalse();
    assertThat(policy.eligible(candidate(List.of("read", "credentials")))).isFalse();
  }

  @Test
  void acceptsCandidatesWithoutBlockedPermissions() {
    assertThat(policy.eligible(candidate(List.of("read", "write")))).isTrue();
    assertThat(policy.eligible(candidate(List.of()))).isTrue();
  }

  @Test
  void matchesBlockedPermissionsCaseInsensitively() {
    assertThat(policy.eligible(candidate(List.of("  SHELL ")))).isFalse();
  }

  @Test
  void supportsACustomBlockedPermissionSet() {
    RemoteCandidatePolicy custom = new RemoteCandidatePolicy(Set.of("network"));
    assertThat(custom.eligible(candidate(List.of("network")))).isFalse();
    assertThat(custom.eligible(candidate(List.of("shell")))).isTrue();
  }

  private static RemoteModuleCandidate candidate(List<String> permissions) {
    return new RemoteModuleCandidate(
        "zalava-module-example", "1.0.0", "a".repeat(64), "Example", "Example", permissions);
  }
}
