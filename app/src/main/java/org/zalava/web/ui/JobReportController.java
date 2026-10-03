package org.zalava.web.ui;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorJobEvidenceQueries;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskNotFoundException;

@Controller
public final class JobReportController {
  private final ActorJobEvidenceQueries evidence;
  private final AuthenticatedActorResolver actors;

  public JobReportController(ActorJobEvidenceQueries evidence, AuthenticatedActorResolver actors) {
    this.evidence = evidence;
    this.actors = actors;
  }

  @GetMapping("/jobs/{reference:[0-9a-fA-F-]{36}}/artifacts/report")
  public ResponseEntity<String> report(
      @PathVariable String reference, Authentication authentication) {
    var actor = actors.actor(authentication);
    try {
      var id = new ActorTaskReference(reference);
      String content = evidence.report(actor, id);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment()
                  .filename("job-report-" + id.value() + ".md")
                  .build()
                  .toString())
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(content);
    } catch (TaskNotFoundException | IllegalArgumentException unavailable) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved job report is unavailable");
    }
  }
}
