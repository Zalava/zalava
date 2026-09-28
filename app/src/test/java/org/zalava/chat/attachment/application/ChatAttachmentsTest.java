package org.zalava.chat.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.attachment.application.port.out.ChatAttachmentStore;
import org.zalava.chat.attachment.application.port.out.KnowledgeImportPort;
import org.zalava.chat.attachment.domain.ChatAttachment;
import org.zalava.chat.attachment.domain.ChatAttachmentIntent;
import org.zalava.chat.attachment.domain.ChatAttachmentNotFoundException;

class ChatAttachmentsTest {
  private final InMemoryStore store = new InMemoryStore();
  private final KnowledgeImportPort imports = new RecordingImports();
  private final ChatAttachments attachments = new ChatAttachments(store, imports, 1024);

  @Test
  void storesTaskOnlyAttachmentsOwnerScopedWithProvenance() {
    Actor owner = actor();
    Actor other = actor();

    ChatAttachments.Reference reference =
        attachments.put(
            owner, ChatAttachmentIntent.TASK_ONLY, "notes.txt", "text/plain", "hello".getBytes());

    assertThat(reference.attachmentId()).isNotBlank();
    assertThat(reference.sourceId()).isNull();
    assertThat(reference.name()).isEqualTo("notes.txt");
    assertThat(reference.contentType()).isEqualTo("text/plain");
    assertThat(reference.byteCount()).isEqualTo(5);
    assertThat(reference.sha256()).hasSize(64);
    assertThat(attachments.list(owner)).hasSize(1);
    assertThat(attachments.list(other)).isEmpty();
    assertThat(
            new String(attachments.read(owner, reference.attachmentId()), StandardCharsets.UTF_8))
        .isEqualTo("hello");
  }

  @Test
  void importsDurablyWithoutStoringATaskOnlyReference() {
    ChatAttachments.Reference reference =
        attachments.put(
            actor(),
            ChatAttachmentIntent.KNOWLEDGE_IMPORT,
            "report.pdf",
            "application/pdf",
            "%PDF-1.4 body".getBytes());

    assertThat(reference.attachmentId()).isNull();
    assertThat(reference.sourceId()).isNotBlank();
    assertThat(store.saved).isEmpty();
    assertThat(((RecordingImports) imports).imported).hasSize(1);
  }

  @Test
  void rejectsUnsupportedTypesAndOversizedContent() {
    assertThatThrownBy(
            () ->
                attachments.put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "notes.txt",
                    "application/zip",
                    "hello".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                attachments.put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "notes.txt",
                    "text/plain",
                    new byte[2048]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                attachments.put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "fake.pdf",
                    "application/pdf",
                    "not a pdf".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deletionAndReadAreOwnerScoped() {
    Actor owner = actor();
    ChatAttachments.Reference reference =
        attachments.put(
            owner, ChatAttachmentIntent.TASK_ONLY, "notes.txt", "text/plain", "hello".getBytes());
    Actor other = actor();

    assertThatThrownBy(() -> attachments.delete(other, reference.attachmentId()))
        .isInstanceOf(ChatAttachmentNotFoundException.class);
    assertThatThrownBy(() -> attachments.read(other, reference.attachmentId()))
        .isInstanceOf(ChatAttachmentNotFoundException.class);

    attachments.delete(owner, reference.attachmentId());
    assertThat(attachments.list(owner)).isEmpty();
  }

  @Test
  void acceptsEverySupportedContentTypeAndBoundsTheName() {
    assertThat(
            attachments
                .put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "a.md",
                    "text/markdown",
                    "# hi".getBytes())
                .name())
        .isEqualTo("a.md");
    assertThat(
            attachments
                .put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "a.html",
                    "text/html",
                    "<p>hi</p>".getBytes())
                .name())
        .isEqualTo("a.html");
    assertThat(
            attachments
                .put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "a.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    new byte[] {'P', 'K', 3, 4, 0})
                .name())
        .isEqualTo("a.docx");
    assertThat(
            attachments
                .put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "a.pdf",
                    "application/pdf",
                    "%PDF-1.4".getBytes())
                .name())
        .isEqualTo("a.pdf");
    assertThat(
            attachments
                .put(actor(), ChatAttachmentIntent.TASK_ONLY, " ", "text/plain", "hi".getBytes())
                .name())
        .isEqualTo("attachment");
    assertThat(
            attachments
                .put(
                    actor(),
                    ChatAttachmentIntent.TASK_ONLY,
                    "x".repeat(300),
                    "text/plain",
                    "hi".getBytes())
                .name())
        .hasSize(200);
  }

  private static Actor actor() {
    return new Actor(AccountId.newId());
  }

  private static final class RecordingImports implements KnowledgeImportPort {
    private final List<String> imported = new ArrayList<>();

    @Override
    public ImportedSource importSource(
        Actor actor, String displayName, String contentType, byte[] content) {
      imported.add(displayName);
      return new ImportedSource("source-" + imported.size());
    }
  }

  private static final class InMemoryStore implements ChatAttachmentStore {
    private final Map<String, ChatAttachment> saved = new LinkedHashMap<>();
    private final Map<String, byte[]> content = new LinkedHashMap<>();

    @Override
    public ChatAttachment save(Actor actor, ChatAttachment attachment, byte[] bytes) {
      saved.put(attachment.id(), attachment);
      content.put(attachment.id(), bytes);
      return attachment;
    }

    @Override
    public Optional<ChatAttachment> find(Actor actor, String attachmentId) {
      ChatAttachment attachment = saved.get(attachmentId);
      return attachment != null && attachment.owner().equals(actor)
          ? Optional.of(attachment)
          : Optional.empty();
    }

    @Override
    public List<ChatAttachment> list(Actor actor) {
      return saved.values().stream()
          .filter(attachment -> attachment.owner().equals(actor))
          .toList();
    }

    @Override
    public Optional<byte[]> read(Actor actor, String attachmentId) {
      return find(actor, attachmentId).map(ignored -> content.get(attachmentId));
    }

    @Override
    public void delete(Actor actor, String attachmentId) {
      saved.remove(attachmentId);
      content.remove(attachmentId);
    }
  }
}
