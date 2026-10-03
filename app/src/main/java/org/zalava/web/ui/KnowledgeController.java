package org.zalava.web.ui;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.knowledge.application.KnowledgeIngestion;
import org.zalava.knowledge.application.KnowledgeLibrary;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.knowledge.domain.KnowledgeVisibility;
import org.zalava.knowledge.domain.SourceProcessingState;

/** Authenticated product read surface for Zalava-owned knowledge metadata. */
@Controller
public final class KnowledgeController {
  private final KnowledgeLibrary library;
  private final AuthenticatedActorResolver actors;
  private final KnowledgeIngestion ingestion;
  private final KnowledgeSourceLifecycle lifecycle;

  public KnowledgeController(
      KnowledgeLibrary library,
      AuthenticatedActorResolver actors,
      KnowledgeIngestion ingestion,
      KnowledgeSourceLifecycle lifecycle) {
    this.library = library;
    this.actors = actors;
    this.ingestion = ingestion;
    this.lifecycle = lifecycle;
  }

  @GetMapping("/knowledge")
  String knowledge(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String contentType,
      @RequestParam(required = false) String processingState,
      Authentication authentication,
      Model model,
      CsrfToken csrf) {
    List<KnowledgeLibrary.SourceSummary> sources;
    String error = null;
    try {
      KnowledgeLibrary.MetadataFilter filter =
          new KnowledgeLibrary.MetadataFilter(
              emptyToNull(contentType), processingState(processingState));
      sources =
          query == null || query.isBlank()
              ? library.browse(actors.actor(authentication), filter, 20)
              : library.search(actors.actor(authentication), query, filter, 20);
    } catch (IllegalArgumentException exception) {
      sources = List.of();
      error = "Search could not be completed.";
    }
    model.addAttribute(
        "model",
        new KnowledgeModel(
            sources,
            query == null ? "" : query,
            contentType == null ? "" : contentType,
            processingState == null ? "" : processingState,
            error,
            csrf));
    return "ui/knowledge";
  }

  @GetMapping("/knowledge/{sourceId}")
  String detail(@PathVariable String sourceId, Authentication authentication, Model model) {
    try {
      KnowledgeLibrary.SourceDetail source =
          library
              .inspect(actors.actor(authentication), sourceId(sourceId))
              .orElseThrow(KnowledgeController::unavailable);
      model.addAttribute("model", source);
      return "ui/knowledge-detail";
    } catch (IllegalArgumentException exception) {
      throw unavailable();
    }
  }

  @org.springframework.web.bind.annotation.PostMapping("/knowledge/upload")
  String upload(
      @RequestParam MultipartFile file,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      ingestion.submit(
          actors.actor(authentication),
          file.getOriginalFilename(),
          file.getContentType(),
          file.getBytes());
      redirect.addFlashAttribute("knowledgeMessage", "Source uploaded for processing.");
    } catch (Exception exception) {
      redirect.addFlashAttribute("knowledgeError", "Source upload could not be completed.");
    }
    return "redirect:/knowledge";
  }

  @org.springframework.web.bind.annotation.PostMapping("/knowledge/{sourceId}/visibility")
  String visibility(
      @PathVariable String sourceId,
      @RequestParam boolean shared,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      lifecycle.changeVisibility(
          actors.actor(authentication),
          sourceId(sourceId),
          shared ? KnowledgeVisibility.GROUP_SHARED : KnowledgeVisibility.PRIVATE);
      redirect.addFlashAttribute("knowledgeMessage", "Source sharing updated.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("knowledgeError", "Source change could not be completed.");
    }
    return "redirect:/knowledge";
  }

  @org.springframework.web.bind.annotation.PostMapping("/knowledge/{sourceId}/delete")
  String delete(
      @PathVariable String sourceId,
      @RequestParam(defaultValue = "false") boolean confirmed,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      if (!confirmed) throw new IllegalArgumentException();
      lifecycle.hardDelete(actors.actor(authentication), sourceId(sourceId));
      redirect.addFlashAttribute("knowledgeMessage", "Source deleted.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("knowledgeError", "Source deletion could not be completed.");
    }
    return "redirect:/knowledge";
  }

  @org.springframework.web.bind.annotation.PostMapping("/knowledge/{sourceId}/retry")
  String retry(
      @PathVariable String sourceId, Authentication authentication, RedirectAttributes redirect) {
    try {
      ingestion.retry(actors.actor(authentication), sourceId(sourceId));
      redirect.addFlashAttribute("knowledgeMessage", "Source reprocessing requested.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("knowledgeError", "Source retry could not be completed.");
    }
    return "redirect:/knowledge";
  }

  private static KnowledgeSourceId sourceId(String value) {
    return new KnowledgeSourceId(java.util.UUID.fromString(value));
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static SourceProcessingState processingState(String value) {
    return value == null || value.isBlank()
        ? null
        : SourceProcessingState.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
  }

  public record KnowledgeModel(
      List<KnowledgeLibrary.SourceSummary> sources,
      String query,
      String contentType,
      String processingState,
      String error,
      CsrfToken csrf) {}
}
