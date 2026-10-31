package org.zalava.web.ui;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;

@Controller
public class ModulePageAccessController {
  private final ModulePageAccess access;
  private final AccountLifecycle accounts;
  private final AuthenticatedActorResolver actors;

  public ModulePageAccessController(
      ModulePageAccess access, AccountLifecycle accounts, AuthenticatedActorResolver actors) {
    this.access = access;
    this.accounts = accounts;
    this.actors = actors;
  }

  @GetMapping("/zalava/accounts/app-access")
  String page(
      @RequestParam(required = false) String accountId,
      Authentication auth,
      Model model,
      CsrfToken csrf) {
    if (accountId != null && accountId.isBlank()) return "redirect:/zalava/accounts/app-access";
    String admin = actors.actor(auth).accountId().toString();
    var members =
        accounts.list().stream()
            .filter(a -> a.role() == AccountRole.MEMBER)
            .map(a -> new Person(a.id().toString(), a.loginName()))
            .toList();
    if (accountId != null && members.stream().noneMatch(a -> a.id().equals(accountId)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    List<Row> pages =
        access.pages(admin).stream()
            .map(
                p ->
                    new Row(
                        p,
                        access.memberDefault(admin, p.moduleId(), p.pageId()),
                        accountId == null
                            ? null
                            : access.override(admin, accountId, p.moduleId(), p.pageId())))
            .toList();
    model.addAttribute("members", members);
    model.addAttribute("accountId", accountId);
    model.addAttribute("pages", pages);
    model.addAttribute("csrf", csrf);
    return "accounts/app-access";
  }

  @PostMapping("/zalava/accounts/app-access/default")
  String setDefault(
      @RequestParam String moduleId,
      @RequestParam String pageId,
      @RequestParam boolean allowed,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      access.setDefault(actors.actor(auth).accountId().toString(), moduleId, pageId, allowed);
      redirect.addFlashAttribute("message", "Member default saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("error", "App access could not be saved. Refresh and try again.");
    }
    return "redirect:/zalava/accounts/app-access";
  }

  @PostMapping("/zalava/accounts/app-access/user")
  String setUser(
      @RequestParam String accountId,
      @RequestParam String moduleId,
      @RequestParam String pageId,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      UUID.fromString(accountId);
      access.setOverride(
          actors.actor(auth).accountId().toString(), accountId, moduleId, pageId, decision);
      redirect.addFlashAttribute("message", "Individual access saved.");
      redirect.addAttribute("accountId", accountId);
      return "redirect:/zalava/accounts/app-access?accountId={accountId}";
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("error", "App access could not be saved. Refresh and try again.");
      return "redirect:/zalava/accounts/app-access";
    }
  }

  public record Person(String id, String login) {}

  public record Row(RegisteredWebPage page, boolean defaultAllowed, String override) {}
}
