package org.zalava.knowledge.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.SeaServiceDescriptor;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.content.ContentExtractionLimits;
import org.zalava.content.ContentExtractionRequest;
import org.zalava.content.ContentExtractionResult;
import org.zalava.content.ContentExtractor;
import org.zalava.content.ContentProcessor;
import org.zalava.knowledge.application.port.out.KnowledgeBlobStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.DerivationState;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.runtime.application.port.in.RuntimeQueries;

class KnowledgeExtractionJobTest {
  private final Actor owner = new Actor(new AccountId(UUID.randomUUID()));
  private final KnowledgeSourceId sourceId = KnowledgeSourceId.create();
  private final KnowledgeSource source =
      new KnowledgeSource(
          sourceId,
          owner,
          "note.txt",
          "text/plain",
          5,
          "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
          KnowledgeVisibility.PRIVATE,
          SourceProcessingState.PENDING,
          Instant.EPOCH,
          Instant.EPOCH,
          0);
  private final KnowledgeDerivation candidate =
      new KnowledgeDerivation(
          sourceId, 1, "zalava-module-tika", "1", DerivationState.CANDIDATE, Instant.EPOCH, 0);

  @Test
  void promotesOnlyAValidSourceCorrelatedExtractorResult() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor = request -> success(request);
    when(sources.findById(sourceId)).thenReturn(Optional.of(source));
    when(blobs.read(sourceId)).thenReturn(Optional.of("hello".getBytes()));
    when(runtime.findService(ContentExtractor.CONTRACT))
        .thenReturn(
            Optional.of(
                new RuntimeQueries.LoadedSeaService<>(
                    new SeaServiceDescriptor("content-extractor", "zalava-module-tika", "1"),
                    extractor)));
    when(lifecycle.beginReprocessing(owner, sourceId, "zalava-module-tika", "1"))
        .thenReturn(candidate);

    new KnowledgeExtractionJob(
            lifecycle,
            sources,
            blobs,
            records,
            runtime,
            new ContentExtractionLimits(10, 100, 2, 20, 2))
        .extract(sourceId);

    verify(lifecycle).completeReprocessing(owner, candidate, true);
  }

  @Test
  void ignoresMissingOrCancelledSourcesBeforeResolvingAnExtractor() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    when(sources.findById(sourceId)).thenReturn(Optional.empty());

    job(lifecycle, sources, blobs, records, runtime).extract(sourceId);

    verifyNoInteractions(blobs, records, runtime);
  }

  @Test
  void failsClosedWhenNoActiveExtractorExists() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    when(sources.findById(sourceId)).thenReturn(Optional.of(source));
    when(runtime.findService(ContentExtractor.CONTRACT)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                job(lifecycle, sources, blobs, records, runtime)
                    .extract(sourceId.value().toString()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No active ContentExtractor");
    verifyNoInteractions(blobs, records);
  }

  @Test
  void persistsTypedFailureAndRetainsCandidateWhenExtractorRejectsInput() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor =
        request ->
            new org.zalava.content.ContentExtractionFailure(
                request.source(),
                new ContentProcessor("tika", "4.0.0"),
                org.zalava.content.ContentExtractionFailureCategory.MALFORMED_INPUT,
                "bad input");
    when(sources.findById(sourceId)).thenReturn(Optional.of(source));
    when(blobs.read(sourceId)).thenReturn(Optional.of("hello".getBytes()));
    when(runtime.findService(ContentExtractor.CONTRACT))
        .thenReturn(
            Optional.of(
                new RuntimeQueries.LoadedSeaService<>(
                    new SeaServiceDescriptor("content-extractor", "zalava-module-tika", "1"),
                    extractor)));
    when(lifecycle.beginReprocessing(owner, sourceId, "zalava-module-tika", "1"))
        .thenReturn(candidate);

    job(lifecycle, sources, blobs, records, runtime).extract(sourceId);

    verify(lifecycle).completeReprocessing(owner, candidate, false);
    verify(records).record(any());
  }

  @Test
  void ignoresCancelledSourceAndDoesNotTouchItsBlob() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    KnowledgeSource cancelled =
        new KnowledgeSource(
            sourceId,
            owner,
            source.displayName(),
            source.contentType(),
            source.byteCount(),
            source.sha256(),
            source.visibility(),
            SourceProcessingState.CANCELLED,
            source.createdAt(),
            source.updatedAt(),
            source.version());
    when(sources.findById(sourceId)).thenReturn(Optional.of(cancelled));

    job(lifecycle, sources, blobs, records, runtime).extract(sourceId);

    verifyNoInteractions(blobs, records, runtime);
  }

  @Test
  void recordsInternalFailureWhenExtractorThrows() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor =
        request -> {
          throw new IllegalStateException("broken parser");
        };
    configureExtractableSource(lifecycle, sources, blobs, runtime, extractor);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> job(lifecycle, sources, blobs, records, runtime).extract(sourceId))
        .isInstanceOf(IllegalStateException.class);

    verify(records).record(any());
    verify(lifecycle).completeReprocessing(owner, candidate, false);
  }

  @Test
  void treatsBlankWorkerDisabledResultAsUnavailableAndNeverPromotesIt() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor =
        request ->
            ContentExtractionResult.forRequest(
                request, new ContentProcessor("apache-tika", "4.0.0"), "  ", Map.of(), List.of());
    configureExtractableSource(lifecycle, sources, blobs, runtime, extractor);

    job(lifecycle, sources, blobs, records, runtime).extract(sourceId);

    verify(records)
        .record(
            org.mockito.ArgumentMatchers.argThat(
                record ->
                    record.failureCategory()
                            == org.zalava.content.ContentExtractionFailureCategory.UNAVAILABLE
                        && record.text() == null));
    verify(lifecycle, org.mockito.Mockito.never()).completeReprocessing(owner, candidate, true);
    verify(lifecycle).completeReprocessing(owner, candidate, false);
  }

  @Test
  void recordsUnavailableWorkerFailureWithoutPromotingItsCandidate() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor =
        request ->
            new org.zalava.content.ContentExtractionFailure(
                request.source(),
                new ContentProcessor("sea-ocr-worker", "1"),
                org.zalava.content.ContentExtractionFailureCategory.UNAVAILABLE,
                "OCR worker is unavailable");
    configureExtractableSource(lifecycle, sources, blobs, runtime, extractor);

    job(lifecycle, sources, blobs, records, runtime).extract(sourceId);

    verify(records)
        .record(
            org.mockito.ArgumentMatchers.argThat(
                record ->
                    record.failureCategory()
                        == org.zalava.content.ContentExtractionFailureCategory.UNAVAILABLE));
    verify(lifecycle).completeReprocessing(owner, candidate, false);
  }

  @Test
  void recordsInternalFailureForAnUncorrelatedExtractorResult() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor =
        request ->
            new ContentExtractionResult(
                new org.zalava.content.ContentSourceMetadata(
                    "other.txt", "text/plain", 1, "a".repeat(64)),
                new ContentProcessor("tika", "4.0.0"),
                "wrong source",
                Map.of(),
                List.of());
    configureExtractableSource(lifecycle, sources, blobs, runtime, extractor);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> job(lifecycle, sources, blobs, records, runtime).extract(sourceId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source-correlated");
    verify(records).record(any());
    verify(lifecycle).completeReprocessing(owner, candidate, false);
  }

  @Test
  void failsWhenAPersistedSourceHasNoOriginalBlob() {
    KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
    KnowledgeSourceStore sources = mock(KnowledgeSourceStore.class);
    KnowledgeBlobStore blobs = mock(KnowledgeBlobStore.class);
    KnowledgeExtractionRecordStore records = mock(KnowledgeExtractionRecordStore.class);
    RuntimeQueries runtime = mock(RuntimeQueries.class);
    ContentExtractor extractor = request -> success(request);
    when(sources.findById(sourceId)).thenReturn(Optional.of(source));
    when(blobs.read(sourceId)).thenReturn(Optional.empty());
    when(runtime.findService(ContentExtractor.CONTRACT))
        .thenReturn(
            Optional.of(
                new RuntimeQueries.LoadedSeaService<>(
                    new SeaServiceDescriptor("content-extractor", "zalava-module-tika", "1"),
                    extractor)));
    when(lifecycle.beginReprocessing(owner, sourceId, "zalava-module-tika", "1"))
        .thenReturn(candidate);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> job(lifecycle, sources, blobs, records, runtime).extract(sourceId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("original is unavailable");
    verifyNoInteractions(records);
  }

  private void configureExtractableSource(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeSourceStore sources,
      KnowledgeBlobStore blobs,
      RuntimeQueries runtime,
      ContentExtractor extractor) {
    when(sources.findById(sourceId)).thenReturn(Optional.of(source));
    when(blobs.read(sourceId)).thenReturn(Optional.of("hello".getBytes()));
    when(runtime.findService(ContentExtractor.CONTRACT))
        .thenReturn(
            Optional.of(
                new RuntimeQueries.LoadedSeaService<>(
                    new SeaServiceDescriptor("content-extractor", "zalava-module-tika", "1"),
                    extractor)));
    when(lifecycle.beginReprocessing(owner, sourceId, "zalava-module-tika", "1"))
        .thenReturn(candidate);
  }

  private KnowledgeExtractionJob job(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeSourceStore sources,
      KnowledgeBlobStore blobs,
      KnowledgeExtractionRecordStore records,
      RuntimeQueries runtime) {
    return new KnowledgeExtractionJob(
        lifecycle,
        sources,
        blobs,
        records,
        runtime,
        new ContentExtractionLimits(10, 100, 2, 20, 2));
  }

  private ContentExtractionResult success(ContentExtractionRequest request) {
    return ContentExtractionResult.forRequest(
        request, new ContentProcessor("tika", "4.0.0"), "hello", Map.of(), List.of());
  }
}
