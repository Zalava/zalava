package org.zalava.web.ui;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.zalava.knowledge.application.KnowledgeProcessingQueries.Status;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;
import org.zalava.knowledge.memory.domain.MemoryScope;

/** Human-readable library labels; persisted domain values and scope policy remain unchanged. */
public final class LibraryPresentation {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private LibraryPresentation() {}

  public static String date(Instant value) {
    return DATE.format(value);
  }

  public static String date(String value) {
    try {
      return date(Instant.parse(value));
    } catch (DateTimeException invalid) {
      return "Date unavailable";
    }
  }

  public static List<MemoryScope> durableScopes() {
    return List.of(MemoryScope.USER, MemoryScope.PROJECT, MemoryScope.AGENT);
  }

  public static String scope(MemoryScope value) {
    return switch (value) {
      case USER -> "Personal preferences";
      case PROJECT -> "Project knowledge";
      case AGENT -> "Agent knowledge";
      case EXECUTION -> "Execution trace";
    };
  }

  public static String provenance(String value) {
    return switch (value) {
      case "proposal" -> "Approved memory proposal";
      case "user" -> "Saved by you";
      case "legacy" -> "Previously saved memory";
      default -> value;
    };
  }

  public static String state(SourceProcessingState value) {
    return switch (value) {
      case PENDING -> "Queued";
      case PROCESSING -> "Processing";
      case READY -> "Ready";
      case FAILED -> "Processing failed";
      case CANCELLED -> "Cancelled";
      case DELETION_REQUESTED -> "Deletion requested";
      case DELETED -> "Deleted";
    };
  }

  public static String fileType(String type) {
    return switch (type) {
      case "text/plain", "text/markdown" -> "Text document";
      case "text/html" -> "HTML document";
      case "application/pdf" -> "PDF document";
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
          "Word document";
      default -> type;
    };
  }

  public static String fileSize(long bytes) {
    if (bytes < 1024) return bytes + (bytes == 1 ? " byte" : " bytes");
    if (bytes < 1024 * 1024) return String.format(Locale.ENGLISH, "%.1f KB", bytes / 1024.0);
    return String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024));
  }

  public static String visibility(KnowledgeVisibility visibility) {
    return visibility == KnowledgeVisibility.PRIVATE ? "Private to you" : "Shared with workspace";
  }

  public static String processing(Status status) {
    return switch (status.state()) {
      case PENDING ->
          status.extractorAvailable()
              ? "Waiting in the processing queue. Refresh to check progress."
              : "Not processed yet.";
      case PROCESSING -> "The document is being processed. Refresh to check progress.";
      case READY -> "The document has been processed and is available to Knowledge search.";
      case FAILED -> "Processing failed. " + failure(status);
      case CANCELLED -> "Processing was cancelled. You can request another attempt.";
      case DELETION_REQUESTED -> "This source is being deleted.";
      case DELETED -> "This source has been deleted.";
    };
  }

  private static String failure(Status status) {
    if (status.failureCategory() == null)
      return "Retry after checking the source and document processing module.";
    return switch (status.failureCategory()) {
      case UNSUPPORTED_MEDIA_TYPE -> "This file format is not supported by the processor.";
      case MALFORMED_INPUT ->
          "The document could not be read. Check that it opens correctly before retrying.";
      case ENCRYPTED -> "The document is encrypted. Upload an accessible copy.";
      case INPUT_LIMIT_EXCEEDED -> "The file exceeds the processor's input limit.";
      case OUTPUT_LIMIT_EXCEEDED -> "The extracted content exceeds the processor's output limit.";
      case TIMED_OUT -> "Processing took too long. Try a smaller document or retry.";
      case UNAVAILABLE ->
          "The processor could not produce readable text. Check the document processing module.";
      case INTERNAL -> "The processor could not complete the request. Check the module and retry.";
    };
  }
}
