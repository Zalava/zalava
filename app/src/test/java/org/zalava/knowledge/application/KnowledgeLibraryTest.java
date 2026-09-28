package org.zalava.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeSearchStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;

class KnowledgeLibraryTest {
  private final Actor owner = new Actor(new AccountId(UUID.randomUUID()));

  @Test
  void filtersSearchCandidatesThroughVisibleSourceIds() {
    KnowledgeSource visible = source(owner, "safe.pdf");
    KnowledgeSource privateToAnother =
        source(new Actor(new AccountId(UUID.randomUUID())), "secret.pdf");
    KnowledgeSourceStore sources = Mockito.mock(KnowledgeSourceStore.class);
    KnowledgeSearchStore search = Mockito.mock(KnowledgeSearchStore.class);
    when(sources.visibleTo(owner)).thenReturn(List.of(visible));
    when(sources.findById(visible.id())).thenReturn(java.util.Optional.of(visible));
    when(search.findCandidates(
            new KnowledgeSearchStore.SearchCriteria("renewal", null, null, 10, owner)))
        .thenReturn(
            List.of(
                new KnowledgeSearchStore.Candidate(privateToAnother.id(), 1),
                new KnowledgeSearchStore.Candidate(visible.id(), 1)));

    assertThat(new KnowledgeLibrary(sources, search).search(owner, " renewal ", 10))
        .extracting(KnowledgeLibrary.SourceSummary::displayName)
        .containsExactly("safe.pdf");
  }

  @Test
  void rejectsBlankOversizedAndUnboundedSearches() {
    KnowledgeSourceStore sources = Mockito.mock(KnowledgeSourceStore.class);
    KnowledgeSearchStore search = Mockito.mock(KnowledgeSearchStore.class);
    KnowledgeLibrary library = new KnowledgeLibrary(sources, search);

    assertThatThrownBy(() -> library.search(owner, " ", 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> library.search(owner, "x".repeat(257), 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> library.search(owner, "renewal", 51))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inspectsOnlySourcesVisibleToTheActor() {
    KnowledgeSource visible = source(owner, "safe.pdf");
    KnowledgeSource privateToAnother =
        source(new Actor(new AccountId(UUID.randomUUID())), "secret.pdf");
    KnowledgeSourceStore sources = Mockito.mock(KnowledgeSourceStore.class);
    KnowledgeSearchStore search = Mockito.mock(KnowledgeSearchStore.class);
    when(sources.visibleTo(owner)).thenReturn(List.of(visible));
    KnowledgeLibrary library = new KnowledgeLibrary(sources, search);

    assertThat(library.inspect(owner, visible.id()))
        .contains(
            new KnowledgeLibrary.SourceDetail(
                visible.id(),
                "safe.pdf",
                "application/pdf",
                1,
                KnowledgeVisibility.PRIVATE,
                SourceProcessingState.READY,
                Instant.EPOCH,
                Instant.EPOCH,
                0));
    assertThat(library.inspect(owner, privateToAnother.id())).isEmpty();
  }

  @Test
  void appliesMetadataFiltersAndBoundsToBrowseAndSearch() {
    KnowledgeSource readyPdf = source(owner, "ready.pdf");
    KnowledgeSource failedPdf =
        source(owner, "failed.pdf", "application/pdf", SourceProcessingState.FAILED);
    KnowledgeSource readyText =
        source(owner, "ready.txt", "text/plain", SourceProcessingState.READY);
    KnowledgeSourceStore sources = Mockito.mock(KnowledgeSourceStore.class);
    KnowledgeSearchStore search = Mockito.mock(KnowledgeSearchStore.class);
    KnowledgeLibrary library = new KnowledgeLibrary(sources, search);
    KnowledgeLibrary.MetadataFilter readyPdfFilter =
        new KnowledgeLibrary.MetadataFilter("application/pdf", SourceProcessingState.READY);
    when(sources.visibleTo(owner)).thenReturn(List.of(readyPdf, failedPdf, readyText));
    when(search.findCandidates(
            new KnowledgeSearchStore.SearchCriteria(
                "renewal", "application/pdf", SourceProcessingState.READY, 1, owner)))
        .thenReturn(List.of(new KnowledgeSearchStore.Candidate(readyPdf.id(), 1)));
    when(sources.findById(readyPdf.id())).thenReturn(java.util.Optional.of(readyPdf));

    assertThat(library.browse(owner, readyPdfFilter, 1))
        .extracting(KnowledgeLibrary.SourceSummary::displayName)
        .containsExactly("ready.pdf");
    assertThat(library.search(owner, "renewal", readyPdfFilter, 1))
        .extracting(KnowledgeLibrary.SourceSummary::displayName)
        .containsExactly("ready.pdf");
    assertThatThrownBy(() -> library.browse(owner, readyPdfFilter, 51))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new KnowledgeLibrary.MetadataFilter("not a type", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revokesAFormerlySharedCandidateBeforeTheNextSearch() {
    KnowledgeSource shared = source(owner, "shared.pdf");
    KnowledgeSourceStore sources = Mockito.mock(KnowledgeSourceStore.class);
    KnowledgeSearchStore search = Mockito.mock(KnowledgeSearchStore.class);
    KnowledgeLibrary library = new KnowledgeLibrary(sources, search);
    KnowledgeSearchStore.SearchCriteria criteria =
        new KnowledgeSearchStore.SearchCriteria("renewal", null, null, 10, owner);
    when(search.findCandidates(criteria))
        .thenReturn(List.of(new KnowledgeSearchStore.Candidate(shared.id(), 1)));
    when(sources.findById(shared.id())).thenReturn(java.util.Optional.of(shared));
    when(sources.visibleTo(owner)).thenReturn(List.of(shared), List.of());

    assertThat(library.search(owner, "renewal", 10)).isNotEmpty();
    assertThat(library.search(owner, "renewal", 10)).isEmpty();
  }

  private static KnowledgeSource source(Actor owner, String name) {
    return source(owner, name, "application/pdf", SourceProcessingState.READY);
  }

  private static KnowledgeSource source(
      Actor owner, String name, String contentType, SourceProcessingState processingState) {
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        owner,
        name,
        contentType,
        1,
        "a".repeat(64),
        KnowledgeVisibility.PRIVATE,
        processingState,
        Instant.EPOCH,
        Instant.EPOCH,
        0);
  }
}
