package org.zalava.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryTest {

  @Test
  void backwardCompatibleConstructorsLeaveTheRecordUnrevised() {
    Instant createdAt = Instant.parse("2026-09-17T10:00:00Z");

    Memory withProvenance =
        new Memory(
            "memory-1",
            MemoryScope.PROJECT,
            "fact",
            Map.of("k", "v"),
            createdAt,
            MemoryProvenance.of("user"));

    assertThat(withProvenance.updatedAt()).isNull();
    assertThat(withProvenance.provenance().source()).isEqualTo("user");

    Memory legacy = new Memory("memory-2", MemoryScope.USER, "fact", Map.of(), createdAt);
    assertThat(legacy.updatedAt()).isNull();
    assertThat(legacy.provenance().source()).isEqualTo(MemoryProvenance.LEGACY_SOURCE);
  }

  @Test
  void revisedPreservesIdentityProvenanceAndMetadata() {
    Instant createdAt = Instant.parse("2026-09-17T10:00:00Z");
    Instant revisedAt = Instant.parse("2026-09-17T12:00:00Z");
    Memory original =
        new Memory(
            "memory-1",
            MemoryScope.USER,
            "before",
            Map.of("k", "v"),
            createdAt,
            MemoryProvenance.of("proposal", "run-1"));

    Memory revised = original.revised(MemoryScope.AGENT, "after", revisedAt);

    assertThat(revised.id()).isEqualTo("memory-1");
    assertThat(revised.createdAt()).isEqualTo(createdAt);
    assertThat(revised.provenance()).isEqualTo(original.provenance());
    assertThat(revised.metadata()).isEqualTo(Map.of("k", "v"));
    assertThat(revised.scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(revised.text()).isEqualTo("after");
    assertThat(revised.updatedAt()).isEqualTo(revisedAt);
  }

  @Test
  void rejectsARevisionTimestampBeforeCreation() {
    Instant createdAt = Instant.parse("2026-09-17T10:00:00Z");

    assertThatThrownBy(
            () ->
                new Memory(
                    "memory-1",
                    MemoryScope.USER,
                    "fact",
                    Map.of(),
                    createdAt,
                    MemoryProvenance.of("user"),
                    createdAt.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("updatedAt");
  }

  @Test
  void rejectsBlankIdentityAndText() {
    assertThatThrownBy(() -> new Memory(" ", MemoryScope.USER, "fact", Map.of(), Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Memory("memory-1", MemoryScope.USER, " ", Map.of(), Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
