package org.zalava.knowledge.application;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.UUID;
import org.zalava.api.extensions.content.ContentExtractionFailure;
import org.zalava.api.extensions.content.ContentExtractionLimits;
import org.zalava.api.extensions.content.ContentExtractionOutcome;
import org.zalava.api.extensions.content.ContentExtractionRequest;
import org.zalava.api.extensions.content.ContentExtractionResult;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.api.extensions.content.ContentSourceInput;
import org.zalava.api.extensions.content.ContentSourceMetadata;
import org.zalava.knowledge.application.port.out.KnowledgeBlobStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.knowledge.domain.KnowledgeDerivation;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Runs exactly one persisted source through the currently active bounded extractor. */
public final class KnowledgeExtractionJob {
  private final KnowledgeSourceLifecycle lifecycle;
  private final KnowledgeSourceStore sources;
  private final KnowledgeBlobStore blobs;
  private final KnowledgeExtractionRecordStore records;
  private final RuntimeQueries runtime;
  private final ContentExtractionLimits limits;

  public KnowledgeExtractionJob(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeSourceStore sources,
      KnowledgeBlobStore blobs,
      KnowledgeExtractionRecordStore records,
      RuntimeQueries runtime,
      ContentExtractionLimits limits) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.sources = Objects.requireNonNull(sources, "sources");
    this.blobs = Objects.requireNonNull(blobs, "blobs");
    this.records = Objects.requireNonNull(records, "records");
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  public void extract(String encodedSourceId) {
    extract(new KnowledgeSourceId(UUID.fromString(encodedSourceId)));
  }

  public void extract(KnowledgeSourceId sourceId) {
    KnowledgeSource source = sources.findById(sourceId).orElse(null);
    if (source == null) {
      return;
    }
    if (source.processingState() == SourceProcessingState.CANCELLED
        || source.processingState() == SourceProcessingState.DELETION_REQUESTED
        || source.processingState() == SourceProcessingState.DELETED) {
      return;
    }
    RuntimeQueries.LoadedZalavaService<ContentExtractor> service =
        runtime.findService(ContentExtractor.CONTRACT).orElse(null);
    if (service == null) {
      throw new IllegalStateException("No active ContentExtractor service is available");
    }
    KnowledgeDerivation candidate =
        lifecycle.beginReprocessing(
            source.owner(),
            sourceId,
            service.descriptor().moduleId(),
            service.descriptor().contractVersion());
    byte[] original =
        blobs
            .read(sourceId)
            .orElseThrow(
                () -> new IllegalStateException("Knowledge source original is unavailable"));
    ContentSourceMetadata metadata =
        new ContentSourceMetadata(
            source.displayName(), source.contentType(), source.byteCount(), source.sha256());
    ContentExtractionRequest request =
        new ContentExtractionRequest(
            metadata,
            ContentSourceInput.singleUse(
                new ByteArrayInputStream(original), limits.maximumInputBytes()),
            limits);
    ContentExtractionOutcome outcome;
    try {
      outcome = service.service().extract(request);
    } catch (RuntimeException exception) {
      records.record(
          org.zalava.knowledge.domain.KnowledgeExtractionRecord.failed(
              candidate,
              org.zalava.api.extensions.content.ContentExtractionFailureCategory.INTERNAL,
              "Extractor execution failed"));
      lifecycle.completeReprocessing(source.owner(), candidate, false);
      throw exception;
    }
    if (outcome instanceof ContentExtractionResult result && result.source().equals(metadata)) {
      if (result.text().isBlank()) {
        records.record(
            org.zalava.knowledge.domain.KnowledgeExtractionRecord.failed(
                candidate,
                org.zalava.api.extensions.content.ContentExtractionFailureCategory.UNAVAILABLE,
                "Extractor returned no text"));
        lifecycle.completeReprocessing(source.owner(), candidate, false);
        return;
      }
      records.record(
          org.zalava.knowledge.domain.KnowledgeExtractionRecord.succeeded(
              candidate, result.text()));
      lifecycle.completeReprocessing(source.owner(), candidate, true);
      return;
    }
    if (outcome instanceof ContentExtractionFailure failure && failure.source().equals(metadata)) {
      records.record(
          org.zalava.knowledge.domain.KnowledgeExtractionRecord.failed(
              candidate, failure.category(), failure.detail()));
      lifecycle.completeReprocessing(source.owner(), candidate, false);
      return;
    }
    records.record(
        org.zalava.knowledge.domain.KnowledgeExtractionRecord.failed(
            candidate,
            org.zalava.api.extensions.content.ContentExtractionFailureCategory.INTERNAL,
            "Extractor returned an invalid outcome"));
    lifecycle.completeReprocessing(source.owner(), candidate, false);
    throw new IllegalStateException(
        "ContentExtractor returned an invalid source-correlated outcome");
  }
}
