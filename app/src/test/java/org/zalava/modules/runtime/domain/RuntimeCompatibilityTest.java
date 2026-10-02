package org.zalava.modules.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeCompatibilityTest {

  @Test
  void evaluatesInclusiveMinimumAndExclusiveMaximum() {
    RuntimeCompatibility range = RuntimeCompatibility.parse(">=1.0.0 <2.0.0");

    assertThat(range.supports(RuntimeVersion.parse("1.0.0"))).isTrue();
    assertThat(range.supports(RuntimeVersion.parse("1.9.9"))).isTrue();
    assertThat(range.supports(RuntimeVersion.parse("2.0.0"))).isFalse();
  }

  @Test
  void rejectsInvalidRangeOrder() {
    assertThatThrownBy(() -> RuntimeCompatibility.parse(">=2.0.0 <2.0.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("minimum must be lower than upper bound");
  }
}
