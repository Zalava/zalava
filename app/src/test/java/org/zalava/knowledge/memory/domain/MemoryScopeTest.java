package org.zalava.knowledge.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemoryScopeTest {

  @Test
  void durableScopesCarryLastingKnowledgeWhileExecutionDoesNot() {
    assertThat(MemoryScope.USER.durable()).isTrue();
    assertThat(MemoryScope.PROJECT.durable()).isTrue();
    assertThat(MemoryScope.AGENT.durable()).isTrue();
    assertThat(MemoryScope.EXECUTION.durable()).isFalse();
  }

  @Test
  void contextEligibilityMatchesDurability() {
    assertThat(MemoryScope.USER.contextEligible()).isTrue();
    assertThat(MemoryScope.PROJECT.contextEligible()).isTrue();
    assertThat(MemoryScope.AGENT.contextEligible()).isTrue();
    assertThat(MemoryScope.EXECUTION.contextEligible()).isFalse();
  }

  @Test
  void durableScopesExcludeExecution() {
    assertThat(MemoryScope.durableScopes())
        .containsExactlyInAnyOrder(MemoryScope.USER, MemoryScope.PROJECT, MemoryScope.AGENT)
        .doesNotContain(MemoryScope.EXECUTION);
  }

  @Test
  void allScopesCoverEveryDefinedValue() {
    assertThat(MemoryScope.all()).containsExactlyInAnyOrder(MemoryScope.values());
  }

  @Test
  void parseAcceptsDefinedScopesCaseInsensitively() {
    assertThat(MemoryScope.parse("user")).isEqualTo(MemoryScope.USER);
    assertThat(MemoryScope.parse(" Project ")).isEqualTo(MemoryScope.PROJECT);
    assertThat(MemoryScope.parse("EXECUTION")).isEqualTo(MemoryScope.EXECUTION);
  }

  @Test
  void parseRejectsBlankAndUnknownScopes() {
    assertThatThrownBy(() -> MemoryScope.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
    assertThatThrownBy(() -> MemoryScope.parse("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
    assertThatThrownBy(() -> MemoryScope.parse("global"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid memory scope");
  }
}
