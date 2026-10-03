package org.zalava.knowledge.memory.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemoryContentPolicyTest {

  @Test
  void acceptsDurableNonSensitiveContent() {
    assertThatCode(() -> MemoryContentPolicy.validate(MemoryScope.USER, "Prefers concise answers"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                MemoryContentPolicy.validate(MemoryScope.PROJECT, "Zalava uses provider instances"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsTransientExecutionScope() {
    assertThatThrownBy(() -> MemoryContentPolicy.validate(MemoryScope.EXECUTION, "ran a tool"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class)
        .hasMessageContaining("Transient execution memory");
  }

  @Test
  void rejectsSecretMarkers() {
    assertThatThrownBy(
            () -> MemoryContentPolicy.validate(MemoryScope.USER, "The password is hunter2"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class)
        .hasMessageContaining("credentials or secrets");
    assertThatThrownBy(
            () -> MemoryContentPolicy.validate(MemoryScope.PROJECT, "client secret for the API"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
  }

  @Test
  void rejectsCredentialLikeTokens() {
    assertThatThrownBy(
            () ->
                MemoryContentPolicy.validate(
                    MemoryScope.PROJECT, "deploy with sk-abcdefghijklmnop1234"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
    assertThatThrownBy(
            () -> MemoryContentPolicy.validate(MemoryScope.AGENT, "ghp_abcdefghijklmnopqrstuvwx"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
  }

  @Test
  void rejectsBlankOversizedAndMissingValues() {
    assertThatThrownBy(() -> MemoryContentPolicy.validate(null, "text"))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
    assertThatThrownBy(() -> MemoryContentPolicy.validate(MemoryScope.USER, "  "))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class);
    assertThatThrownBy(
            () ->
                MemoryContentPolicy.validate(
                    MemoryScope.USER, "x".repeat(MemoryProposalDraft.MAXIMUM_TEXT_LENGTH + 1)))
        .isInstanceOf(MemoryContentPolicy.UnsafeMemoryContentException.class)
        .hasMessageContaining("must not exceed");
  }
}
