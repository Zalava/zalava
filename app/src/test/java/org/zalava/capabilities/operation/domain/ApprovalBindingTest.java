package org.zalava.capabilities.operation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApprovalBindingTest {
  @Test
  void matchesOnlyTheSameActorModuleScopeRoleConversationAndArguments() {
    var args = new LinkedHashMap<String, Object>();
    args.put("target", "room");
    args.put("data", Map.of("level", List.of(1, 2)));
    args.put("optional", null);
    var binding =
        new ApprovalBinding("actor", "module", Map.of("tenant", "one"), "MEMBER", "chat", args);
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "module", Map.of("tenant", "one"), "MEMBER", "chat", args)))
        .isTrue();
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "other", "module", Map.of("tenant", "one"), "MEMBER", "chat", args)))
        .isFalse();
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "other", Map.of("tenant", "one"), "MEMBER", "chat", args)))
        .isFalse();
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "module", Map.of("tenant", "two"), "MEMBER", "chat", args)))
        .isFalse();
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "module", Map.of("tenant", "one"), "ADMIN", "chat", args)))
        .isFalse();
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "module", Map.of("tenant", "one"), "MEMBER", "other", args)))
        .isFalse();
    var changed = new LinkedHashMap<>(args);
    changed.put("data", Map.of("level", List.of(1, 3)));
    assertThat(
            binding.matches(
                new ApprovalBinding(
                    "actor", "module", Map.of("tenant", "one"), "MEMBER", "chat", changed)))
        .isFalse();
    assertThat(binding.matches(null)).isFalse();
  }

  @Test
  void missingActorOrModuleNeverMatches() {
    var missingActor = new ApprovalBinding(null, "module", Map.of(), null, null, Map.of());
    var missingModule = new ApprovalBinding("actor", null, Map.of(), null, null, Map.of());
    assertThat(missingActor.matches(missingActor)).isFalse();
    assertThat(missingModule.matches(missingModule)).isFalse();
  }
}
