package org.zalava.ui;

import java.util.List;
import java.util.Set;
import org.zalava.accounts.domain.Actor;
import org.zalava.accounts.security.AuthenticatedActorResolver;
import org.zalava.memory.application.port.in.ActorMemoryQueries;
import org.zalava.memory.application.port.in.MemoryManagement;
import org.zalava.memory.application.port.in.MemoryPromotions;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.memory.domain.MemoryProposalDraft;
import org.zalava.memory.domain.MemoryScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Authenticated product surface for actor-private durable memory: browse/search, inspect, edit and
 * delete entries, and review the promotion proposals the model is allowed to raise. Ownership is
 * always resolved from the authenticated principal; a memory or proposal id never authorizes.
 */
@Controller
public final class MemoryController {

  private static final int PAGE_SIZE = 50;

  private final ActorMemoryQueries memories;
  private final MemoryPromotions promotions;
  private final MemoryManagement management;
  private final AuthenticatedActorResolver actors;

  public MemoryController(
      ActorMemoryQueries memories,
      MemoryPromotions promotions,
      MemoryManagement management,
      AuthenticatedActorResolver actors) {
    this.memories = memories;
    this.promotions = promotions;
    this.management = management;
    this.actors = actors;
  }

  @GetMapping("/memory")
  String memory(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String scope,
      Authentication authentication,
      Model model,
      CsrfToken csrf) {
    Actor actor = actors.actor(authentication);
    String error = null;
    Set<MemoryScope> scopes = MemoryScope.all();
    if (scope != null && !scope.isBlank()) {
      try {
        scopes = Set.of(MemoryScope.parse(scope));
      } catch (IllegalArgumentException exception) {
        error = "Unknown memory scope.";
      }
    }
    List<Memory> entries;
    try {
      entries =
          query == null || query.isBlank()
              ? memories.recent(actor, scopes, PAGE_SIZE)
              : memories.search(actor, scopes, query, PAGE_SIZE);
    } catch (IllegalArgumentException exception) {
      entries = List.of();
      error = "Memory search could not be completed.";
    }
    model.addAttribute(
        "model",
        new MemoryModel(
            entries,
            promotions.pending(actor),
            query == null ? "" : query,
            scope == null ? "" : scope,
            error,
            csrf));
    return "ui/memory";
  }

  @GetMapping("/memory/{memoryId}")
  String detail(
      @PathVariable String memoryId, Authentication authentication, Model model, CsrfToken csrf) {
    Memory memory =
        memories
            .find(actors.actor(authentication), memoryId)
            .orElseThrow(MemoryController::unavailable);
    model.addAttribute("model", MemoryDetailModel.of(memory, csrf));
    return "ui/memory-detail";
  }

  @PostMapping("/memory/{memoryId}/edit")
  String edit(
      @PathVariable String memoryId,
      @RequestParam String scope,
      @RequestParam String text,
      Authentication authentication,
      Model model,
      CsrfToken csrf) {
    Actor actor = actors.actor(authentication);
    try {
      management
          .revise(actor, memoryId, MemoryScope.parse(scope), text)
          .orElseThrow(MemoryController::unavailable);
      return "redirect:/memory/" + memoryId;
    } catch (IllegalArgumentException exception) {
      Memory memory = memories.find(actor, memoryId).orElseThrow(MemoryController::unavailable);
      model.addAttribute(
          "model", new MemoryDetailModel(memory, scope, text, message(exception), csrf));
      return "ui/memory-detail";
    }
  }

  @PostMapping("/memory/{memoryId}/delete")
  String delete(
      @PathVariable String memoryId,
      @RequestParam(defaultValue = "false") boolean confirmed,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      if (!confirmed) throw new IllegalArgumentException();
      management.delete(actors.actor(authentication), memoryId);
      redirect.addFlashAttribute("memoryMessage", "Memory deleted.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("memoryError", "Memory deletion could not be completed.");
    }
    return "redirect:/memory";
  }

  @PostMapping("/memory/proposals")
  String propose(
      @RequestParam String scope,
      @RequestParam String text,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      promotions.propose(
          actors.actor(authentication), MemoryProposalDraft.of(MemoryScope.parse(scope), text));
      redirect.addFlashAttribute("memoryMessage", "Memory proposed for review.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("memoryError", message(exception));
    }
    return "redirect:/memory";
  }

  @PostMapping("/memory/proposals/{proposalId}/approve")
  String approve(
      @PathVariable String proposalId, Authentication authentication, RedirectAttributes redirect) {
    return decide(proposalId, authentication, redirect, Decision.APPROVE);
  }

  @PostMapping("/memory/proposals/{proposalId}/reject")
  String reject(
      @PathVariable String proposalId, Authentication authentication, RedirectAttributes redirect) {
    return decide(proposalId, authentication, redirect, Decision.REJECT);
  }

  @PostMapping("/memory/proposals/{proposalId}/revoke")
  String revoke(
      @PathVariable String proposalId, Authentication authentication, RedirectAttributes redirect) {
    return decide(proposalId, authentication, redirect, Decision.REVOKE);
  }

  private String decide(
      String proposalId,
      Authentication authentication,
      RedirectAttributes redirect,
      Decision decision) {
    try {
      Actor actor = actors.actor(authentication);
      switch (decision) {
        case APPROVE -> {
          promotions.approve(actor, proposalId);
          redirect.addFlashAttribute("memoryMessage", "Memory approved and stored.");
        }
        case REJECT -> {
          promotions.reject(actor, proposalId, null);
          redirect.addFlashAttribute("memoryMessage", "Proposal rejected.");
        }
        case REVOKE -> {
          promotions.revoke(actor, proposalId);
          redirect.addFlashAttribute("memoryMessage", "Proposal revoked.");
        }
      }
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("memoryError", message(exception));
    }
    return "redirect:/memory";
  }

  private static String message(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? "Memory change could not be completed."
        : exception.getMessage();
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  private enum Decision {
    APPROVE,
    REJECT,
    REVOKE
  }

  public record MemoryModel(
      List<Memory> memories,
      List<MemoryProposal> proposals,
      String query,
      String scope,
      String error,
      CsrfToken csrf) {}

  public record MemoryDetailModel(
      Memory memory, String scope, String text, String error, CsrfToken csrf) {

    static MemoryDetailModel of(Memory memory, CsrfToken csrf) {
      return new MemoryDetailModel(memory, memory.scope().name(), memory.text(), null, csrf);
    }
  }
}
