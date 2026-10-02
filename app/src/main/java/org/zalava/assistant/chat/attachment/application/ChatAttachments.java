package org.zalava.assistant.chat.attachment.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.zalava.assistant.chat.attachment.application.port.out.ChatAttachmentStore;
import org.zalava.assistant.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.assistant.chat.attachment.domain.ChatAttachment;
import org.zalava.assistant.chat.attachment.domain.ChatAttachmentIntent;
import org.zalava.assistant.chat.attachment.domain.ChatAttachmentNotFoundException;
import org.zalava.identity.accounts.domain.Actor;

/**
 * Accepts bounded attachments with an explicit retention intent. SEA owns the reference; the
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

  public ChatAttachments(
      ChatAttachmentStore store, KnowledgeImportPort knowledgeImports, long maximumUploadBytes) {
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

  public List<Reference> list(Actor actor) {
    return store.list(actor).stream()
        .map(attachment -> reference(attachment, ChatAttachmentIntent.TASK_ONLY, null))
        .toList();
  }

  public Reference reference(Actor actor, String attachmentId) {
    ChatAttachment attachment =
        store
            .find(actor, attachmentId)
            .orElseThrow(() -> new ChatAttachmentNotFoundException(attachmentId));
    return reference(attachment, ChatAttachmentIntent.TASK_ONLY, null);
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
