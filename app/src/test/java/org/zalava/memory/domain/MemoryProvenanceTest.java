package org.zalava.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemoryProvenanceTest {

  @Test
  void legacyMarksRecordsThatPredateProvenance() {
    assertThat(MemoryProvenance.legacy().source()).isEqualTo("legacy");
    assertThat(MemoryProvenance.legacy().reference()).isNull();
  }

  @Test
  void normalizesSourceAndOptionalReference() {
    assertThat(MemoryProvenance.of("  task  ")).isEqualTo(new MemoryProvenance("task", null));
    assertThat(MemoryProvenance.of("task", " run-1 ").reference()).isEqualTo("run-1");
    assertThat(MemoryProvenance.of("task", "   ").reference()).isNull();
    assertThat(MemoryProvenance.of("task", null).reference()).isNull();
  }

  @Test
  void rejectsBlankSourceAndOversizedValues() {
    assertThatThrownBy(() -> MemoryProvenance.of(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MemoryProvenance.of("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source must not be blank");
    assertThatThrownBy(() -> MemoryProvenance.of("s".repeat(65)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source must not exceed 64 characters");
    assertThatThrownBy(() -> MemoryProvenance.of("task", "r".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference must not exceed 200 characters");
  }
}
