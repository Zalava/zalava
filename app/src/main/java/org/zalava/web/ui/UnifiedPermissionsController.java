package org.zalava.web.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;

@Controller
public class UnifiedPermissionsController {
  private final MemberToolAccess tools;
  private final ChatToolConfirmation confirmation;
  private final ModulePageAccess pages;
  private final AccountLifecycle accounts;
  private final AuthenticatedActorResolver actors;

  public UnifiedPermissionsController(
      MemberToolAccess tools,
      ChatToolConfirmation confirmation,
      ModulePageAccess pages,
      AccountLifecycle accounts,
      AuthenticatedActorResolver actors) {
    this.tools = tools;
    this.confirmation = confirmation;
    this.pages = pages;
    this.accounts = accounts;
    this.actors = actors;
  }

  @GetMapping("/zalava/accounts/permissions")
  String page(
      @RequestParam(defaultValue = "member") String subject,
      Authentication auth,
      Model model,
      CsrfToken csrf) {
    String admin = actors.actor(auth).accountId().toString();
    Subject selected = subject(subject);
    var people =
        accounts.list().stream()
            .map(a -> new Person(a.id().toString(), a.loginName(), a.role(), a.enabled()))
            .toList();
    Map<ToolKey, MemberToolAccess.ToolChoice> choices = new LinkedHashMap<>();
    tools.availableTools(admin).forEach(t -> choices.put(t.key(), t));
    Map<ToolKey, ChatToolConfirmation.Tool> confirmationTools = new LinkedHashMap<>();
    confirmation.tools(admin).forEach(t -> confirmationTools.put(t.key(), t));
    tools
        .defaults(admin)
        .forEach(
            d ->
                choices.putIfAbsent(
                    d.key(),
                    new MemberToolAccess.ToolChoice(
                        d.key(),
                        d.key().providerId(),
                        "Provider unavailable; saved settings remain active when it starts.",
                        false)));
    if (selected.person() != null && selected.role() == AccountRole.MEMBER)
      tools
          .decisions(admin, selected.person().id().toString())
          .forEach(
              d ->
                  choices.putIfAbsent(
                      d.key(),
                      new MemberToolAccess.ToolChoice(
                          d.key(),
                          d.key().providerId(),
                          "Provider unavailable; saved settings remain active when it starts.",
                          false)));
    confirmationTools.forEach(
        (key, t) ->
            choices.putIfAbsent(
                key,
                new MemberToolAccess.ToolChoice(key, t.providerName(), t.description(), false)));
    List<ToolRow> toolRows = new ArrayList<>();
    for (var tool : choices.values()) {
      var key = tool.key();
      var ct = confirmationTools.get(key);
      boolean available = ct != null && ct.available();
      Boolean sideEffecting = ct == null ? null : ct.sideEffecting();
      String accessChoice = "FIXED", accessEffective = "Allowed · Administrator", accessSource = "";
      if (selected.role() == AccountRole.MEMBER) {
        if (selected.person() == null) {
          boolean allowed =
              tools.defaults(admin).stream().anyMatch(d -> d.key().equals(key) && d.allowed());
          accessChoice = allowed ? "ALLOW" : "DENY";
          accessEffective = allowed && tool.eligible() ? "Allowed" : "Not allowed";
          accessSource = tool.eligible() ? "Member role default" : "Unavailable or restricted";
        } else {
          var e = tools.effective(admin, selected.person().id().toString(), key);
          accessChoice = e.override();
          accessEffective =
              e.allowed() && selected.person().enabled() && tool.eligible()
                  ? "Allowed"
                  : "Not allowed";
          accessSource = tool.eligible() ? e.source() : "Unavailable or restricted";
        }
      }
      Choice c =
          selected.person() == null
              ? confirmation.roleDefault(admin, selected.role(), key)
              : confirmation.effective(admin, selected.person().id().toString(), key).override();
      var effective =
          selected.person() == null
              ? confirmation.roleDefault(admin, selected.role(), key)
              : confirmation.effective(admin, selected.person().id().toString(), key).effective();
      String source =
          selected.person() == null
              ? "Role default"
              : confirmation.effective(admin, selected.person().id().toString(), key).source();
      toolRows.add(
          new ToolRow(
              key,
              friendly(key.toolName()),
              tool.providerName(),
              tool.description(),
              tool.eligible(),
              available,
              sideEffecting,
              accessChoice,
              accessEffective,
              accessSource,
              c,
              effective,
              source));
    }
    toolRows.sort(
        Comparator.comparing((ToolRow r) -> r.key().moduleId())
            .thenComparing(r -> r.providerName())
            .thenComparing(ToolRow::name));
    List<PageRow> appRows =
        pages.pages(admin).stream()
            .map(
                p -> {
                  boolean defaultAllowed = pages.memberDefault(admin, p.moduleId(), p.pageId());
                  String override =
                      selected.person() != null && selected.role() == AccountRole.MEMBER
                          ? pages.override(
                              admin, selected.person().id().toString(), p.moduleId(), p.pageId())
                          : "INHERIT";
                  boolean allowed =
                      selected.role() == AccountRole.ADMIN
                          || (override.equals("ALLOW")
                              || (override.equals("INHERIT") && defaultAllowed));
                  if (p.routes().isEmpty()
                      || (selected.person() != null && !selected.person().enabled()))
                    allowed = false;
                  String choice =
                      selected.role() == AccountRole.ADMIN
                          ? "FIXED"
                          : selected.person() == null
                              ? (defaultAllowed ? "ALLOW" : "DENY")
                              : override;
                  return new PageRow(
                      p,
                      choice,
                      allowed,
                      p.routes().isEmpty()
                          ? "App unavailable"
                          : selected.person() == null
                              ? "Role default"
                              : override.equals("INHERIT")
                                  ? "Role default"
                                  : "Individual override");
                })
            .toList();
    model.addAttribute("people", people);
    model.addAttribute("subject", subject);
    model.addAttribute("personSelected", selected.person() != null);
    model.addAttribute("memberSubject", selected.role() == AccountRole.MEMBER);
    model.addAttribute("accountEnabled", selected.person() == null || selected.person().enabled());
    model.addAttribute("toolRows", toolRows);
    model.addAttribute("appRows", appRows);
    model.addAttribute("csrf", csrf);
    return "accounts/permissions";
  }

  @PostMapping("/zalava/accounts/permissions/tool-access")
  String toolAccess(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      String admin = actors.actor(auth).accountId().toString();
      Subject s = subject(subject);
      ToolKey key = new ToolKey(moduleId, providerId, toolName);
      if (s.role() != AccountRole.MEMBER)
        throw new IllegalArgumentException("Administrator access is fixed");
      if (s.person() == null) {
        if (!List.of("ALLOW", "DENY").contains(decision))
          throw new IllegalArgumentException("Invalid choice");
        tools.defaultAccess(admin, key, decision.equals("ALLOW"));
      } else if (decision.equals("INHERIT")) tools.inherit(admin, s.person().id().toString(), key);
      else {
        if (!List.of("ALLOW", "DENY").contains(decision))
          throw new IllegalArgumentException("Invalid choice");
        tools.decide(admin, s.person().id().toString(), key, decision.equals("ALLOW"));
      }
      redirect.addFlashAttribute("message", "Tool access saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "Tool access could not be saved. Check availability and restrictions.");
    }
    redirect.addAttribute("subject", subject);
    return "redirect:/zalava/accounts/permissions?subject={subject}";
  }

  @PostMapping("/zalava/accounts/permissions/confirmation")
  String confirm(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      String admin = actors.actor(auth).accountId().toString();
      Subject s = subject(subject);
      ToolKey key = new ToolKey(moduleId, providerId, toolName);
      Choice choice = Choice.valueOf(decision);
      if (s.person() == null) confirmation.setRole(admin, s.role(), key, choice);
      else confirmation.setUser(admin, s.person().id().toString(), key, choice);
      redirect.addFlashAttribute("message", "Chat confirmation saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "Confirmation could not be saved. Check whether the tool is available.");
    }
    redirect.addAttribute("subject", subject);
    return "redirect:/zalava/accounts/permissions?subject={subject}";
  }

  @PostMapping("/zalava/accounts/permissions/app-access")
  String appAccess(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String pageId,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      String admin = actors.actor(auth).accountId().toString();
      Subject s = subject(subject);
      if (s.role() != AccountRole.MEMBER)
        throw new IllegalArgumentException("Administrator access is fixed");
      if (s.person() == null) {
        if (!List.of("ALLOW", "DENY").contains(decision))
          throw new IllegalArgumentException("Invalid choice");
        pages.setDefault(admin, moduleId, pageId, decision.equals("ALLOW"));
      } else pages.setOverride(admin, s.person().id().toString(), moduleId, pageId, decision);
      redirect.addFlashAttribute("message", "App access saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "App access could not be saved. Check whether the app is available.");
    }
    redirect.addAttribute("subject", subject);
    return "redirect:/zalava/accounts/permissions?subject={subject}";
  }

  private Subject subject(String value) {
    if (value.equals("member")) return new Subject(AccountRole.MEMBER, null);
    if (value.equals("administrator")) return new Subject(AccountRole.ADMIN, null);
    return accounts.list().stream()
        .filter(a -> a.id().toString().equals(value))
        .findFirst()
        .map(a -> new Subject(a.role(), a))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private static String friendly(String name) {
    String text = name.replace('_', ' ').replace('-', ' ').strip();
    return text.isEmpty() ? name : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  record Subject(AccountRole role, Account person) {}

  public record Person(String id, String name, AccountRole role, boolean enabled) {}

  public record ToolRow(
      ToolKey key,
      String name,
      String providerName,
      String description,
      boolean eligible,
      boolean available,
      Boolean sideEffecting,
      String accessChoice,
      String accessEffective,
      String accessSource,
      Choice confirmationChoice,
      Choice confirmationEffective,
      String confirmationSource) {}

  public record PageRow(RegisteredWebPage page, String choice, boolean allowed, String source) {}
}
