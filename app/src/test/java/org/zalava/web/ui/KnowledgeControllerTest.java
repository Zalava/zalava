package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.application.KnowledgeLibrary;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;

class KnowledgeControllerTest {
  private final AccountLifecycle accounts = mock(AccountLifecycle.class);
  private final KnowledgeLibrary library = mock(KnowledgeLibrary.class);
  private final KnowledgeIngestion ingestion = mock(KnowledgeIngestion.class);
  private final KnowledgeSourceLifecycle lifecycle = mock(KnowledgeSourceLifecycle.class);
  private final Actor actor = new Actor(new AccountId(UUID.randomUUID()));
  private final UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
          "knowledge-owner", "unused", List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
  private KnowledgeController controller;

  @BeforeEach
  void setUp() {
    when(accounts.findByLoginName("knowledge-owner"))
        .thenReturn(
            Optional.of(
                new Account(
                    actor.accountId(),
                    "knowledge-owner",
                    "password-hash",
                    true,
                    AccountRole.MEMBER,
                    false,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    0)));
    controller =
        new KnowledgeController(
            library, new AuthenticatedActorResolver(accounts), ingestion, lifecycle);
  }

  @Test
  void uploadsThroughIngestionForTheAuthenticatedOwner() throws Exception {
    var redirect = redirects();

    assertThat(
            controller.upload(
                new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes()),
                authentication,
                redirect))
        .isEqualTo("redirect:/knowledge");

    verify(ingestion).submit(actor, "notes.txt", "text/plain", "notes".getBytes());
    assertThat(redirect.getFlashAttributes().get("knowledgeMessage"))
        .isEqualTo("Source uploaded for processing.");
  }

  @Test
  void redactsUploadFailures() throws Exception {
    doThrow(new IllegalArgumentException("internal upload detail"))
        .when(ingestion)
        .submit(eq(actor), any(), any(), any());
    var redirect = redirects();

    controller.upload(
        new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes()),
        authentication,
        redirect);

    assertThat(redirect.getFlashAttributes().get("knowledgeError"))
        .isEqualTo("Source upload could not be completed.")
        .isNotEqualTo("internal upload detail");
  }

  @Test
  void changesVisibilityDeletesConfirmedSourcesAndRetriesForTheOwner() {
    KnowledgeSourceId sourceId = KnowledgeSourceId.create();

    var visibilityRedirect = redirects();
    controller.visibility(sourceId.value().toString(), true, authentication, visibilityRedirect);
    verify(lifecycle).changeVisibility(actor, sourceId, KnowledgeVisibility.GROUP_SHARED);
    assertThat(visibilityRedirect.getFlashAttributes().get("knowledgeMessage"))
        .isEqualTo("Source sharing updated.");

    var deleteRedirect = redirects();
    controller.delete(sourceId.value().toString(), true, authentication, deleteRedirect);
    verify(lifecycle).hardDelete(actor, sourceId);
    assertThat(deleteRedirect.getFlashAttributes().get("knowledgeMessage"))
        .isEqualTo("Source deleted.");

    var retryRedirect = redirects();
    controller.retry(sourceId.value().toString(), authentication, retryRedirect);
    verify(ingestion).retry(actor, sourceId);
    assertThat(retryRedirect.getFlashAttributes().get("knowledgeMessage"))
        .isEqualTo("Source reprocessing requested.");
  }

  @Test
  void redactsMalformedAndRejectedSourceMutations() {
    KnowledgeSourceId sourceId = KnowledgeSourceId.create();
    doThrow(new IllegalStateException("private lifecycle detail"))
        .when(lifecycle)
        .changeVisibility(actor, sourceId, KnowledgeVisibility.PRIVATE);
    doThrow(new IllegalStateException("private retry detail"))
        .when(ingestion)
        .retry(actor, sourceId);

    var visibilityRedirect = redirects();
    controller.visibility(sourceId.value().toString(), false, authentication, visibilityRedirect);
    assertThat(visibilityRedirect.getFlashAttributes().get("knowledgeError"))
        .isEqualTo("Source change could not be completed.")
        .isNotEqualTo("private lifecycle detail");

    var deleteRedirect = redirects();
    controller.delete(sourceId.value().toString(), false, authentication, deleteRedirect);
    assertThat(deleteRedirect.getFlashAttributes().get("knowledgeError"))
        .isEqualTo("Source deletion could not be completed.");

    var retryRedirect = redirects();
    controller.retry(sourceId.value().toString(), authentication, retryRedirect);
    assertThat(retryRedirect.getFlashAttributes().get("knowledgeError"))
        .isEqualTo("Source retry could not be completed.")
        .isNotEqualTo("private retry detail");
  }

  private static RedirectAttributesModelMap redirects() {
    return new RedirectAttributesModelMap();
  }
}
