package org.zalava.channelidentity.adapter.in.http;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.channelidentity.application.port.in.ChannelIdentityLinks;
import org.zalava.channelidentity.application.port.in.ChannelLinkChallenges;
import org.zalava.channelidentity.domain.ChannelIdentityLink;
import org.zalava.channelidentity.domain.ChannelOperationScope;

@RestController
@RequestMapping("/api/channel-links")
public final class ChannelLinkController {
  private static final ChannelOperationScope INITIAL_SCOPE = ChannelOperationScope.of("chat:send");
  private final ChannelLinkChallenges challenges;
  private final ChannelIdentityLinks links;
  private final AuthenticatedActorResolver actors;

  public ChannelLinkController(
      ChannelLinkChallenges challenges,
      ChannelIdentityLinks links,
      AuthenticatedActorResolver actors) {
    this.challenges = challenges;
    this.links = links;
    this.actors = actors;
  }

  @PostMapping("/challenges")
  public ChallengeResponse issue(
      @RequestBody ChallengeRequest request, Authentication authentication) {
    var issued = challenges.issue(actors.actor(authentication), request.channel(), INITIAL_SCOPE);
    return new ChallengeResponse(issued.code(), issued.expiresAt());
  }

  @GetMapping
  public List<LinkResponse> links(Authentication authentication) {
    return links.links(actors.actor(authentication)).stream().map(LinkResponse::from).toList();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication authentication) {
    links.revoke(actors.actor(authentication), id);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
    return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
  }

  public record ChallengeRequest(String channel) {}

  public record ChallengeResponse(String code, Instant expiresAt) {}

  public record LinkResponse(
      UUID id,
      String channel,
      String subject,
      List<String> operations,
      Instant linkedAt,
      Instant revokedAt) {
    static LinkResponse from(ChannelIdentityLink value) {
      return new LinkResponse(
          value.id(),
          value.identity().channel(),
          value.identity().subject(),
          value.scope().operations().stream().sorted().toList(),
          value.linkedAt(),
          value.revokedAt());
    }
  }
}
