package org.zalava.knowledge.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import org.zalava.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeIngestionScheduler;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** Accepts bounded digital documents and requests durable extraction work. */
public final class KnowledgeIngestion {
  private static final Set<String> ACCEPTED_TYPES =
      Set.of(
          "application/pdf",
          "text/plain",
          "text/html",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  private final KnowledgeSourceLifecycle lifecycle;
  private final KnowledgeIngestionScheduler scheduler;
  private final long maximumUploadBytes;

  public KnowledgeIngestion(
      KnowledgeSourceLifecycle lifecycle,
      KnowledgeIngestionScheduler scheduler,
      long maximumUploadBytes) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    if (maximumUploadBytes < 1) {
      throw new IllegalArgumentException("maximumUploadBytes must be positive");
    }
    this.maximumUploadBytes = maximumUploadBytes;
  }

  public KnowledgeSource submit(
      Actor actor, String displayName, String contentType, byte[] content) {
    validate(contentType, content);
    KnowledgeSource source = lifecycle.register(actor, displayName, contentType, content);
    try {
      scheduler.enqueue(source.id());
      return source;
    } catch (RuntimeException exception) {
      lifecycle.hardDelete(actor, source.id());
      throw exception;
    }
  }

  public void retry(Actor actor, KnowledgeSourceId sourceId) {
    lifecycle.requireOwned(actor, sourceId);
    scheduler.enqueue(sourceId);
  }

  public void cancel(Actor actor, KnowledgeSourceId sourceId) {
    lifecycle.requireOwned(actor, sourceId);
    scheduler.cancel(sourceId);
    lifecycle.cancelReprocessing(actor, sourceId);
  }

  private void validate(String contentType, byte[] content) {
    if (!ACCEPTED_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("Unsupported knowledge content type");
    }
    if (content == null || content.length == 0 || content.length > maximumUploadBytes) {
      throw new IllegalArgumentException("Knowledge upload exceeds configured bounds");
    }
    if (!matchesDeclaredType(contentType, content)) {
      throw new IllegalArgumentException(
          "Knowledge upload content does not match its declared type");
    }
  }

  private boolean matchesDeclaredType(String contentType, byte[] content) {
    return switch (contentType) {
      case "application/pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
          content.length >= 4
              && content[0] == 'P'
              && content[1] == 'K'
              && content[2] == 3
              && content[3] == 4;
      case "text/html" ->
          new String(content, StandardCharsets.UTF_8).stripLeading().startsWith("<");
      case "text/plain" -> isValidUtf8(content);
      default -> false;
    };
  }

  private static boolean startsWith(byte[] content, byte[] prefix) {
    if (content.length < prefix.length) {
      return false;
    }
    for (int index = 0; index < prefix.length; index++) {
      if (content[index] != prefix[index]) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidUtf8(byte[] content) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content));
      return true;
    } catch (CharacterCodingException exception) {
      return false;
    }
  }
}
