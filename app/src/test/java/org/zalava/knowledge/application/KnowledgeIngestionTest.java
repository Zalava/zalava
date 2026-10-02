package org.zalava.knowledge.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.port.out.KnowledgeIngestionScheduler;
import org.zalava.knowledge.domain.KnowledgeSource;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;

class KnowledgeIngestionTest {

  private final KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
  private final KnowledgeIngestionScheduler scheduler = mock(KnowledgeIngestionScheduler.class);
  private final KnowledgeIngestion ingestion = new KnowledgeIngestion(lifecycle, scheduler, 10);

  @Test
  void rejectsUnsupportedTypeBeforeAnySourceOrJobIsCreated() {
    assertThatThrownBy(
            () ->
                ingestion.submit(
                    mock(Actor.class), "source.exe", "application/octet-stream", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(lifecycle, scheduler);
  }

  @Test
  void rejectsEmptyAndOversizedContentBeforeAnySourceOrJobIsCreated() {
    assertThatThrownBy(
            () -> ingestion.submit(mock(Actor.class), "empty.txt", "text/plain", new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ingestion.submit(mock(Actor.class), "large.txt", "text/plain", new byte[11]))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(lifecycle, scheduler);
  }

  @Test
  void rejectsContentThatDoesNotMatchItsDeclaredDigitalFormat() {
    assertThatThrownBy(
            () ->
                ingestion.submit(
                    mock(Actor.class), "not-a-pdf.pdf", "application/pdf", "plain text".getBytes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");

    verifyNoInteractions(lifecycle, scheduler);
  }

  @Test
  void registersEachAcceptedTextSubmissionAndEnqueuesIt() {
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    KnowledgeSource source = source(actor);
    when(lifecycle.register(actor, "note.txt", "text/plain", "hello".getBytes()))
        .thenReturn(source);

    KnowledgeSource submitted =
        ingestion.submit(actor, "note.txt", "text/plain", "hello".getBytes());

    org.assertj.core.api.Assertions.assertThat(submitted).isEqualTo(source);
    verify(scheduler).enqueue(source.id());
  }

  @Test
  void validatesRecognizablePdfDocxAndHtmlContentBeforeRegistration() {
    assertThatThrownBy(
            () ->
                ingestion.submit(
                    mock(Actor.class), "bad.pdf", "application/pdf", "not pdf".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ingestion.submit(
                    mock(Actor.class),
                    "bad.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "not zip".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ingestion.submit(mock(Actor.class), "bad.html", "text/html", "plain".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(lifecycle, scheduler);
  }

  @Test
  void acceptsRecognizablePdfDocxAndHtmlContent() {
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    KnowledgeSource source = source(actor);
    when(lifecycle.register(
            org.mockito.ArgumentMatchers.eq(actor),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(source);

    ingestion.submit(actor, "a.pdf", "application/pdf", "%PDF-1".getBytes());
    ingestion.submit(
        actor,
        "a.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        new byte[] {'P', 'K', 3, 4});
    ingestion.submit(actor, "a.html", "text/html", "<p>ok</p>".getBytes());

    verify(scheduler, times(3)).enqueue(source.id());
  }

  @Test
  void removesTheNewSourceWhenSchedulingFailsAndAuthorizesRetryAndCancel() {
    Actor actor = new Actor(new AccountId(UUID.randomUUID()));
    KnowledgeSource source = source(actor);
    when(lifecycle.register(actor, "note.txt", "text/plain", "hello".getBytes()))
        .thenReturn(source);
    org.mockito.Mockito.doThrow(new IllegalStateException("scheduler offline"))
        .when(scheduler)
        .enqueue(source.id());

    assertThatThrownBy(() -> ingestion.submit(actor, "note.txt", "text/plain", "hello".getBytes()))
        .isInstanceOf(IllegalStateException.class);
    verify(lifecycle).hardDelete(actor, source.id());

    reset(scheduler);
    ingestion.retry(actor, source.id());
    ingestion.cancel(actor, source.id());
    verify(lifecycle, times(2)).requireOwned(actor, source.id());
    verify(lifecycle).cancelReprocessing(actor, source.id());
  }

  private KnowledgeSource source(Actor actor) {
    return new KnowledgeSource(
        KnowledgeSourceId.create(),
        actor,
        "note.txt",
        "text/plain",
        5,
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        KnowledgeVisibility.PRIVATE,
        SourceProcessingState.PENDING,
        Instant.EPOCH,
        Instant.EPOCH,
        0);
  }
}
