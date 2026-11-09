package org.zalava.web.ui;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.assistant.chat.application.ConversationEvidenceQueries;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;

/** HTTP adapter for owner-scoped conversation evidence. */
@RestController
public final class ConversationEvidenceController {
  private final ConversationEvidenceQueries evidence;
  private final AuthenticatedActorResolver actors;

  public ConversationEvidenceController(
      ConversationEvidenceQueries evidence, AuthenticatedActorResolver actors) {
    this.evidence = evidence;
    this.actors = actors;
  }

  @GetMapping("/chat/{conversationId}/evidence")
  public ConversationEvidenceQueries.Evidence evidence(
      @PathVariable String conversationId, Authentication authentication) {
    try {
      return evidence.evidence(actors.actor(authentication), conversationId);
    } catch (ConversationEvidenceQueries.EvidenceNotFound missing) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }
}
