package org.zalava.memory.adapter.in.http;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.memory.application.SeaMemoryPromotions;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.domain.MemoryContentPolicy;
import org.zalava.memory.domain.MemoryProposal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actor-owned web surface for reviewable durable-memory proposals. The authenticated principal is
 * the only source of ownership; proposal ids never authorize access. Approval is an explicit owner
 * action and is never performed by the model.
 */
@RestController
@RequestMapping("/api/memory-proposals")
public final class MemoryProposalController {

  private final MemoryPromotions promotions;
  private final AuthenticatedActorResolver actors;

  public MemoryProposalController(MemoryPromotions promotions, AuthenticatedActorResolver actors) {
    this.promotions = promotions;
    this.actors = actors;
  }

  @GetMapping
  public List<MemoryProposalResponse> pending(Authentication authentication) {
    return promotions.pending(actors.actor(authentication)).stream()
        .map(MemoryProposalResponse::from)
        .toList();
  }

  @GetMapping("/{proposalId}")
  public MemoryProposalResponse get(
      @PathVariable String proposalId, Authentication authentication) {
    return MemoryProposalResponse.from(promotions.get(actors.actor(authentication), proposalId));
  }

  @PostMapping("/{proposalId}/approve")
  public MemoryProposalResponse approve(
      @PathVariable String proposalId, Authentication authentication) {
    return MemoryProposalResponse.from(
        promotions.approve(actors.actor(authentication), proposalId));
  }

  @PostMapping("/{proposalId}/reject")
  public MemoryProposalResponse reject(
      @PathVariable String proposalId,
      @RequestBody(required = false) RejectRequest request,
      Authentication authentication) {
    String reason = request == null ? null : request.reason();
    return MemoryProposalResponse.from(
        promotions.reject(actors.actor(authentication), proposalId, reason));
  }

  @PostMapping("/{proposalId}/revoke")
  public MemoryProposalResponse revoke(
      @PathVariable String proposalId, Authentication authentication) {
    return MemoryProposalResponse.from(promotions.revoke(actors.actor(authentication), proposalId));
  }

  @ExceptionHandler(SeaMemoryPromotions.NotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(SeaMemoryPromotions.NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(MemoryProposal.StaleProposalException.class)
  ResponseEntity<Map<String, String>> stale(MemoryProposal.StaleProposalException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(MemoryContentPolicy.UnsafeMemoryContentException.class)
  ResponseEntity<Map<String, String>> unsafe(
      MemoryContentPolicy.UnsafeMemoryContentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.getMessage()));
  }

  public record RejectRequest(String reason) {}

  public record MemoryProposalResponse(
      String proposalId,
      String status,
      String scope,
      String text,
      Map<String, String> metadata,
      String provenanceSource,
      String provenanceReference,
      String createdAt,
      String resolvedAt,
      String memoryId,
      List<EventResponse> history) {

    static MemoryProposalResponse from(MemoryProposal proposal) {
      return new MemoryProposalResponse(
          proposal.id(),
          proposal.status().name(),
          proposal.scope().name(),
          proposal.text(),
          proposal.metadata(),
          proposal.provenance().source(),
          proposal.provenance().reference(),
          proposal.createdAt(),
          proposal.resolvedAt(),
          proposal.memoryId(),
          proposal.history().stream().map(EventResponse::from).toList());
    }
  }

  public record EventResponse(String type, String at, String detail) {
    static EventResponse from(MemoryProposal.Event event) {
      return new EventResponse(event.type(), event.at(), event.detail());
    }
  }
}
