package org.zalava.capabilities.discovery.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ZalavaToolCallbackNamesTest {

  @Test
  void createsStableModelSafeProviderScopedNames() {
    String first = ZalavaToolCallbackNames.forTool("workspace-files", "readText");
    String second = ZalavaToolCallbackNames.forTool("other-files", "readText");

    assertThat(first)
        .matches("[A-Za-z0-9_-]+")
        .startsWith("zalava_readText_")
        .hasSizeLessThanOrEqualTo(64);
    assertThat(ZalavaToolCallbackNames.forTool("workspace-files", "readText")).isEqualTo(first);
    assertThat(second).isNotEqualTo(first);
    assertThat(ZalavaToolCallbackNames.isZalavaTool(first)).isTrue();
    assertThat(ZalavaToolCallbackNames.isZalavaTool("createTask")).isFalse();
  }

  @Test
  void rejectsBlankProviderAndToolNames() {
    assertThatThrownBy(() -> ZalavaToolCallbackNames.forTool(" ", "readText"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zalava tool callback providerId must not be blank");
    assertThatThrownBy(() -> ZalavaToolCallbackNames.forTool("workspace-files", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zalava tool callback toolName must not be blank");
  }
}
