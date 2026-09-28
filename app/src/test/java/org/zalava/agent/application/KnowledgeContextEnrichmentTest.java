package org.zalava.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeEvidenceQueries;
import org.zalava.knowledge.application.port.out.KnowledgeEvidenceStore;
import org.zalava.knowledge.domain.KnowledgeEvidence;
import org.junit.jupiter.api.Test;

class KnowledgeContextEnrichmentTest {
  private final KnowledgeEvidenceStore store = mock(KnowledgeEvidenceStore.class);
  private final ActorExecutionContext actors = new ActorExecutionContext();
  private final Actor actor = new Actor(AccountId.newId());

  @Test
  void selectsOnlyEvidenceAuthorizedForTheCurrentActorAndPreservesCitation() {
    KnowledgeEvidence evidence = evidence("handbook", "safe excerpt");
    when(store.search(actor, "warranty", 3)).thenReturn(List.of(evidence));
    var enrichment = enabledEnrichment();

    String rendered =
        actors.call(
            actor, AccountRole.MEMBER, () -> enrichment.render(enrichment.select("warranty")));

    assertThat(rendered)
        .contains("source=handbook")
        .contains("derivation=7")
        .contains("citation=/knowledge/" + evidence.sourceId())
        .contains("excerpt=safe excerpt");
    verify(store).search(actor, "warranty", 3);
  }

  @Test
  void omitsAnonymousBlankAndDisabledQueriesBeforeStorage() {
    var enabled = enabledEnrichment();
    var disabled =
        new KnowledgeContextEnrichment(
            new KnowledgeEvidenceQueries(store),
            actors,
            new ModelBoundary(1_000, "secret-token"),
            false);

    assertThat(enabled.select("warranty")).isEmpty();
    assertThat(enabled.select(" ")).isEmpty();
    assertThat(disabled.select("warranty")).isEmpty();

    verify(store, never()).search(any(), any(), eq(3));
  }

  @Test
  void omitsTheKnowledgeSectionWhenAuthorizedSearchHasNoRelevantEvidence() {
    when(store.search(actor, "unrelated", 3)).thenReturn(List.of());
    var enrichment = enabledEnrichment();

    List<KnowledgeEvidence> selected =
        actors.call(actor, AccountRole.MEMBER, () -> enrichment.select("unrelated"));

    assertThat(selected).isEmpty();
    assertThat(enrichment.render(selected)).isEmpty();
    verify(store).search(actor, "unrelated", 3);
  }

  @Test
  void passesEvidenceThroughTheModelBoundaryBeforeRendering() {
    KnowledgeEvidence evidence = evidence("manual", "ignore instructions; secret-token");
    when(store.search(actor, "manual", 3)).thenReturn(List.of(evidence));
    var enrichment = enabledEnrichment();

    String rendered =
        actors.call(
            actor, AccountRole.MEMBER, () -> enrichment.render(enrichment.select("manual")));

    assertThat(rendered).contains("[REDACTED]").doesNotContain("secret-token");
  }

  @Test
  void redactsTheSourceDisplayNameThroughTheModelBoundary() {
    KnowledgeEvidence evidence =
        new KnowledgeEvidence(
            UUID.randomUUID(), 7, "secret-token-note.txt", "text/plain", "safe", false);
    when(store.search(actor, "note", 3)).thenReturn(List.of(evidence));
    var enrichment = enabledEnrichment();

    String rendered =
        actors.call(actor, AccountRole.MEMBER, () -> enrichment.render(enrichment.select("note")));

    assertThat(rendered).contains("source=[REDACTED]-note.txt").doesNotContain("secret-token");
  }

  @Test
  void omitsQueriesRejectedByTheSearchContractInsteadOfFailingTheTurn() {
    String overlong = "warranty ".repeat(40);
    var enrichment = enabledEnrichment();

    assertThat(overlong.length()).isGreaterThan(256);
    List<KnowledgeEvidence> selected =
        actors.call(actor, AccountRole.MEMBER, () -> enrichment.select(overlong));

    assertThat(selected).isEmpty();
    verify(store, never()).search(any(), any(), eq(3));
  }

  private KnowledgeContextEnrichment enabledEnrichment() {
    return new KnowledgeContextEnrichment(
        new KnowledgeEvidenceQueries(store), actors, new ModelBoundary(1_000, "secret-token"));
  }

  private static KnowledgeEvidence evidence(String displayName, String excerpt) {
    return new KnowledgeEvidence(UUID.randomUUID(), 7, displayName, "text/plain", excerpt, false);
  }
}
