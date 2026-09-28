package org.zalava.skills.adapter.in.http;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.skills.application.port.in.SkillQueries;
import org.zalava.skills.domain.SkillDescriptor;

/**
 * Read-only, actor/policy-aware skill metadata discovery. It returns descriptors only and never
 * loads skill bodies or executes a referenced tool; content activation is a separate surface.
 */
@RestController
@RequestMapping("/api/skills")
public final class SkillController {

  private static final int DEFAULT_SEARCH_LIMIT = 20;

  private final SkillQueries skills;
  private final AuthenticatedActorResolver actors;

  public SkillController(SkillQueries skills, AuthenticatedActorResolver actors) {
    this.skills = skills;
    this.actors = actors;
  }

  @GetMapping
  public List<SkillResponse> installed(Authentication authentication) {
    return skills.installed(actors.role(authentication)).stream().map(SkillResponse::from).toList();
  }

  @GetMapping("/active")
  public List<SkillResponse> active(Authentication authentication) {
    return skills.active(actors.role(authentication)).stream().map(SkillResponse::from).toList();
  }

  @GetMapping("/remote")
  public List<SkillResponse> remote(Authentication authentication) {
    return skills.remote(actors.role(authentication)).stream().map(SkillResponse::from).toList();
  }

  @GetMapping("/search")
  public List<SkillResponse> search(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "limit", required = false) Integer limit,
      Authentication authentication) {
    int bounded = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
    return skills.search(actors.role(authentication), query, bounded).stream()
        .map(SkillResponse::from)
        .toList();
  }

  @GetMapping("/{name}")
  public ResponseEntity<SkillResponse> find(
      @PathVariable String name, Authentication authentication) {
    return skills
        .find(actors.role(authentication), name)
        .map(SkillResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<java.util.Map<String, String>> illegalArgument(
      IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(java.util.Map.of("error", exception.getMessage()));
  }

  public record SkillResponse(
      String name,
      String version,
      String status,
      String description,
      List<String> capabilities,
      List<String> recommendedTools,
      List<String> policyConstraints,
      List<String> validationSteps,
      String seaApiVersion,
      String visibility,
      String provenanceSource,
      String provenanceReference) {

    static SkillResponse from(SkillDescriptor descriptor) {
      return new SkillResponse(
          descriptor.name(),
          descriptor.version(),
          descriptor.status().name(),
          descriptor.description(),
          descriptor.capabilities(),
          descriptor.recommendedTools(),
          descriptor.policyConstraints(),
          descriptor.validationSteps(),
          descriptor.seaApiVersion(),
          descriptor.visibility().name(),
          descriptor.provenance().source(),
          descriptor.provenance().reference());
    }
  }
}
