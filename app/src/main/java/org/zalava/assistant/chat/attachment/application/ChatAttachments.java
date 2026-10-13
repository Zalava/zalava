package org.zalava.assistant.chat.attachment.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.zalava.api.extensions.content.ContentExtractionFailure;
import org.zalava.api.extensions.content.ContentExtractionLimits;
import org.zalava.api.extensions.content.ContentExtractionRequest;
import org.zalava.api.extensions.content.ContentExtractionResult;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.api.extensions.content.ContentSourceInput;
import org.zalava.api.extensions.content.ContentSourceMetadata;
import org.zalava.assistant.agent.domain.AgentRequest;
import org.zalava.assistant.chat.attachment.application.port.out.ChatAttachmentStore;
import org.zalava.assistant.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.assistant.chat.attachment.domain.ChatAttachment;
import org.zalava.assistant.chat.attachment.domain.ChatAttachmentIntent;
import org.zalava.assistant.chat.attachment.domain.ChatAttachmentNotFoundException;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/**
 * Accepts bounded attachments with an explicit retention intent. Zalava owns the reference; the
 * browser never supplies a path, owner or storage location.
 */
public final class ChatAttachments {
  private static final Set<String> ACCEPTED_TYPES =
      Set.of(
          "application/pdf",
          "text/plain",
          "text/markdown",
          "text/html",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private final ChatAttachmentStore store;
  private final KnowledgeImportPort knowledgeImports;
  private final long maximumUploadBytes;
  private final RuntimeQueries runtime;

  public ChatAttachments(
      ChatAttachmentStore store, KnowledgeImportPort knowledgeImports, long maximumUploadBytes) {
    this(store, knowledgeImports, maximumUploadBytes, null);
  }

  public ChatAttachments(
      ChatAttachmentStore store,
      KnowledgeImportPort knowledgeImports,
      long maximumUploadBytes,
      RuntimeQueries runtime) {
    this.runtime = runtime;
    this.store = Objects.requireNonNull(store, "store");
    this.knowledgeImports = Objects.requireNonNull(knowledgeImports, "knowledgeImports");
    if (maximumUploadBytes < 1) {
      throw new IllegalArgumentException("maximumUploadBytes must be positive");
    }
    this.maximumUploadBytes = maximumUploadBytes;
  }

  public Reference put(
      Actor actor,
      ChatAttachmentIntent intent,
      String displayName,
      String contentType,
      byte[] content) {
    validate(contentType, content);
    String name = displayName == null || displayName.isBlank() ? "attachment" : displayName.strip();
    if (name.length() > 200) {
      name = name.substring(0, 200);
    }
    if (intent == ChatAttachmentIntent.KNOWLEDGE_IMPORT) {
      KnowledgeImportPort.ImportedSource imported =
          knowledgeImports.importSource(actor, name, contentType, content);
      return new Reference(
          null, intent, name, contentType, content.length, sha256(content), imported.sourceId());
    }
    ChatAttachment attachment =
        ChatAttachment.create(actor, name, contentType, content.length, sha256(content));
    ChatAttachment saved = store.save(actor, attachment, content);
    return reference(saved, ChatAttachmentIntent.TASK_ONLY, null);
  }

  public synchronized Reference importExisting(Actor actor, String attachmentId) {
    ChatAttachment attachment =
        store
            .find(actor, attachmentId)
            .orElseThrow(() -> new ChatAttachmentNotFoundException(attachmentId));
    if (attachment.knowledgeSourceId() != null
        && knowledgeImports.sourceAvailable(actor, attachment.knowledgeSourceId()))
      return reference(
          attachment, ChatAttachmentIntent.KNOWLEDGE_IMPORT, attachment.knowledgeSourceId());
    byte[] bytes = read(actor, attachmentId);
    validate(attachment.contentType(), bytes);
    if (bytes.length != attachment.byteCount() || !sha256(bytes).equals(attachment.sha256()))
      throw new AttachmentReadingException(
          "This file no longer matches its upload. Remove it and attach it again.");
    var imported =
        knowledgeImports.importSource(
            actor, attachment.displayName(), attachment.contentType(), bytes);
    var saved = store.save(actor, attachment.withKnowledgeSource(imported.sourceId()), bytes);
    return reference(saved, ChatAttachmentIntent.KNOWLEDGE_IMPORT, imported.sourceId());
  }

  public boolean readingAvailable() {
    return runtime != null && runtime.findService(ContentExtractor.CONTRACT).isPresent();
  }

  public AgentRequest prepare(Actor actor, String question, List<String> attachmentIds) {
    if (attachmentIds.isEmpty()) return AgentRequest.question(question);
    if (attachmentIds.size() > 5)
      throw new AttachmentReadingException("Use no more than five attached files per message.");
    var extractor =
        runtime == null ? null : runtime.findService(ContentExtractor.CONTRACT).orElse(null);
    if (extractor == null)
      throw new AttachmentReadingException(
          "Reading attached files needs an active document-processing module. Ask your administrator to install, configure and start one from Modules. Your files are stored, but have not been read.");
    StringBuilder context = new StringBuilder();
    var names = new ArrayList<String>();
    for (String id : attachmentIds) {
      Reference reference = reference(actor, id);
      byte[] bytes = read(actor, id);
      if (bytes.length != reference.byteCount() || !sha256(bytes).equals(reference.sha256()))
        throw new AttachmentReadingException(
            "This file no longer matches its upload. Remove it and attach it again.");
      ContentSourceMetadata source =
          new ContentSourceMetadata(
              reference.name(), reference.contentType(), reference.byteCount(), reference.sha256());
      ContentExtractionLimits limits =
          new ContentExtractionLimits(maximumUploadBytes, 16_000, 32, 1024, 64);
      try (var stream = new ByteArrayInputStream(bytes)) {
        var request =
            new ContentExtractionRequest(
                source, ContentSourceInput.singleUse(stream, maximumUploadBytes), limits);
        var outcome = extractor.service().extract(request);
        if (outcome instanceof ContentExtractionFailure failure && failure.source().equals(source))
          throw new AttachmentReadingException(
              "The document-processing module could not read this file ("
                  + failure.category().name().toLowerCase(Locale.ROOT)
                  + "). Check its supported formats or use a smaller file.");
        if (!(outcome instanceof ContentExtractionResult result) || !result.source().equals(source))
          throw new AttachmentReadingException(
              "The document-processing module returned an invalid result. Ask your administrator to check the module.");
        ContentExtractionResult.forRequest(
            request, result.processor(), result.text(), result.metadata(), result.locations());
        if (result.text().isBlank())
          throw new AttachmentReadingException(
              "No readable text was found in this file. Scanned documents may need an OCR module.");
        context
            .append("File: ")
            .append(reference.name())
            .append("\n")
            .append(result.text())
            .append("\n\n");
        if (context.length() > 20_000)
          throw new AttachmentReadingException(
              "The attached documents are too large for one message. Use fewer or smaller files.");
        names.add(reference.name());
      } catch (AttachmentReadingException exception) {
        throw exception;
      } catch (IOException | RuntimeException exception) {
        throw new AttachmentReadingException(
            "The document-processing module could not read this file within the configured bounds. Check the module or use a smaller file.");
      }
    }
    return new AgentRequest(question, context.toString(), names);
  }

  public List<Reference> list(Actor actor) {
    return store.list(actor).stream()
        .map(
            attachment ->
                reference(attachment, ChatAttachmentIntent.TASK_ONLY, availableSource(attachment)))
        .toList();
  }

  private String availableSource(ChatAttachment attachment) {
    String id = attachment.knowledgeSourceId();
    return id != null && knowledgeImports.sourceAvailable(attachment.owner(), id) ? id : null;
  }

  public Reference reference(Actor actor, String attachmentId) {
    ChatAttachment attachment =
        store
            .find(actor, attachmentId)
            .orElseThrow(() -> new ChatAttachmentNotFoundException(attachmentId));
    return reference(attachment, ChatAttachmentIntent.TASK_ONLY, availableSource(attachment));
  }

  public byte[] read(Actor actor, String attachmentId) {
    return store
        .read(actor, attachmentId)
        .orElseThrow(() -> new ChatAttachmentNotFoundException(attachmentId));
  }

  public void delete(Actor actor, String attachmentId) {
    store
        .find(actor, attachmentId)
        .orElseThrow(() -> new ChatAttachmentNotFoundException(attachmentId));
    store.delete(actor, attachmentId);
  }

  private static Reference reference(
      ChatAttachment attachment, ChatAttachmentIntent intent, String sourceId) {
    return new Reference(
        attachment.id(),
        intent,
        attachment.displayName(),
        attachment.contentType(),
        attachment.byteCount(),
        attachment.sha256(),
        sourceId);
  }

  private void validate(String contentType, byte[] content) {
    if (!ACCEPTED_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("Unsupported attachment content type");
    }
    if (content == null || content.length == 0 || content.length > maximumUploadBytes) {
      throw new IllegalArgumentException("Attachment exceeds configured bounds");
    }
    if (!matchesDeclaredType(contentType, content)) {
      throw new IllegalArgumentException("Attachment content does not match its declared type");
    }
  }

  private static boolean matchesDeclaredType(String contentType, byte[] content) {
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
      case "text/plain", "text/markdown" -> isValidUtf8(content);
      default -> false;
    };
  }

  private static boolean startsWith(byte[] content, byte[] prefix) {
    if (content.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (content[index] != prefix[index]) return false;
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

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /** A bounded, server-issued attachment reference. */
  public record Reference(
      String attachmentId,
      ChatAttachmentIntent intent,
      String name,
      String contentType,
      long byteCount,
      String sha256,
      String sourceId) {}
}
