package org.zalava.skills.adapter.in.http;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.skills.application.SeaSkillActivations;
import org.zalava.skills.application.port.in.SkillActivations;
import org.zalava.skills.domain.SkillActivation;
import org.zalava.skills.domain.SkillActivationDeniedException;
import org.zalava.skills.domain.SkillContentPolicy;
import org.zalava.skills.domain.SkillStaleVersionException;
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
 * Actor-owned, policy-controlled skill activation over HTTP.
 *
 * <p>Activation only records/rolls back a selection and returns bounded metadata; it never returns
 * a skill body, grants a tool or widens a permission.
 */
@RestController
@RequestMapping("/api/skills")
public final class SkillActivationController {

  private final SkillActivations activations;
  private final AuthenticatedActorResolver actors;

  public SkillActivationController(
      SkillActivations activations, AuthenticatedActorResolver actors) {
    this.activations = activations;
    this.actors = actors;
  }

  @GetMapping("/activations")
  public List<SkillActivationResponse> active(Authentication authentication) {
    return activations.active(actors.actor(authentication)).stream()
        .map(SkillActivationResponse::from)
        .toList();
  }

  @GetMapping("/activations/{name}")
  public ResponseEntity<SkillActivationResponse> find(
      @PathVariable String name, Authentication authentication) {
    return activations
        .find(actors.actor(authentication), name)
        .map(SkillActivationResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  @PostMapping("/{name}/activate")
  public SkillActivationResponse activate(
      @PathVariable String name,
      @RequestBody(required = false) ActivateRequest request,
      Authentication authentication) {
    String version = request == null ? null : request.version();
    return SkillActivationResponse.from(
        activations.activate(
            actors.actor(authentication), actors.role(authentication), name, version));
  }

  @PostMapping("/{name}/deactivate")
  public SkillActivationResponse deactivate(
      @PathVariable String name, Authentication authentication) {
    return SkillActivationResponse.from(activations.deactivate(actors.actor(authentication), name));
  }

  @ExceptionHandler(SeaSkillActivations.NotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(SeaSkillActivations.NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(exception));
  }

  @ExceptionHandler(SkillActivationDeniedException.class)
  ResponseEntity<Map<String, String>> denied(SkillActivationDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(exception));
  }

  @ExceptionHandler(SkillStaleVersionException.class)
  ResponseEntity<Map<String, String>> stale(SkillStaleVersionException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error(exception));
  }

  @ExceptionHandler(SkillContentPolicy.UnsafeSkillContentException.class)
  ResponseEntity<Map<String, String>> unsafe(
      SkillContentPolicy.UnsafeSkillContentException exception) {
    return ResponseEntity.badRequest().body(error(exception));
  }

  @ExceptionHandler(SkillContentPolicy.SkillContentBudgetExceededException.class)
  ResponseEntity<Map<String, String>> overBudget(
      SkillContentPolicy.SkillContentBudgetExceededException exception) {
    return ResponseEntity.badRequest().body(error(exception));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(error(exception));
  }

  private static Map<String, String> error(RuntimeException exception) {
    return Map.of("error", exception.getMessage());
  }

  public record ActivateRequest(String version) {}

  public record SkillActivationResponse(
      String name,
      String version,
      String state,
      String contentDigest,
      int contentCharacters,
      String activatedAt,
      String updatedAt,
      List<EventResponse> history) {

    static SkillActivationResponse from(SkillActivation activation) {
      return new SkillActivationResponse(
          activation.name(),
          activation.version(),
          activation.state().name(),
          activation.contentDigest(),
          activation.content().length(),
          activation.activatedAt(),
          activation.updatedAt(),
          activation.history().stream().map(EventResponse::from).toList());
    }
  }

  public record EventResponse(String type, String at, String detail) {
    static EventResponse from(SkillActivation.Event event) {
      return new EventResponse(event.type(), event.at(), event.detail());
    }
  }
}
