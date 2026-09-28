package org.zalava.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class KnowledgeSourceTest {
  private static final Instant CREATED = Instant.parse("2026-08-27T20:00:00Z");
  private static final Actor OWNER = new Actor(new AccountId(UUID.randomUUID()));

  @Test
  void isPrivateByDefaultAndCanOnlyBecomeSharedThroughAnExplicitTransition() {
    KnowledgeSource source = source(KnowledgeVisibility.PRIVATE);

    assertThat(source.share(CREATED.plusSeconds(1)).visibility())
        .isEqualTo(KnowledgeVisibility.GROUP_SHARED);
    assertThat(source.share(CREATED.plusSeconds(1)).unshare(CREATED.plusSeconds(2)).visibility())
        .isEqualTo(KnowledgeVisibility.PRIVATE);
  }

  @Test
  void rejectsMalformedIntegrityEvidence() {
    assertThatThrownBy(
            () ->
                new KnowledgeSource(
                    KnowledgeSourceId.create(),
                    OWNER,
                    "document.txt",
                    "text/plain",
                    1,
                    "not-a-digest",
                    KnowledgeVisibility.PRIVATE,
                    SourceProcessingState.PENDING,
                    CREATED,
                    CREATED,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");
  }

  @Test
  void deletionRetainsOnlyTheLifecycleMarker() {
    assertThat(
            source(KnowledgeVisibility.GROUP_SHARED)
                .requestDeletion(CREATED.plusSeconds(1))
                .deleted(CREATED.plusSeconds(2)))
        .extracting(KnowledgeSource::visibility, KnowledgeSource::processingState)
        .containsExactly(KnowledgeVisibility.PRIVATE, SourceProcessingState.DELETED);
  }

  private static KnowledgeSource source(KnowledgeVisibility visibility) {
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        OWNER,
        "document.txt",
        "text/plain",
        1,
        "a".repeat(64),
        visibility,
        SourceProcessingState.PENDING,
        CREATED,
        CREATED,
        0);
  }
}
