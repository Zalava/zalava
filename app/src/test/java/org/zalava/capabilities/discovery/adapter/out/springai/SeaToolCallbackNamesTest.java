package org.zalava.capabilities.discovery.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SeaToolCallbackNamesTest {

  @Test
  void createsStableModelSafeProviderScopedNames() {
    String first = SeaToolCallbackNames.forTool("workspace-files", "readText");
    String second = SeaToolCallbackNames.forTool("other-files", "readText");

    assertThat(first)
        .matches("[A-Za-z0-9_-]+")
        .startsWith("sea_readText_")
        .hasSizeLessThanOrEqualTo(64);
    assertThat(SeaToolCallbackNames.forTool("workspace-files", "readText")).isEqualTo(first);
    assertThat(second).isNotEqualTo(first);
    assertThat(SeaToolCallbackNames.isSeaTool(first)).isTrue();
    assertThat(SeaToolCallbackNames.isSeaTool("createTask")).isFalse();
  }

  @Test
  void rejectsBlankProviderAndToolNames() {
    assertThatThrownBy(() -> SeaToolCallbackNames.forTool(" ", "readText"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEA tool callback providerId must not be blank");
    assertThatThrownBy(() -> SeaToolCallbackNames.forTool("workspace-files", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEA tool callback toolName must not be blank");
  }
}
