package org.zalava.web.ui;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.Decision;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;

@Controller
public class MemberToolAccessController {
  private final MemberToolAccess access;
  private final AccountLifecycle accounts;
  private final AuthenticatedActorResolver actors;

  public MemberToolAccessController(
      MemberToolAccess access, AccountLifecycle accounts, AuthenticatedActorResolver actors) {
    this.access = access;
    this.accounts = accounts;
    this.actors = actors;
  }

  @GetMapping("/zalava/accounts/{accountId}/tools")
  String tools(
      @PathVariable String accountId, Authentication authentication, Model model, CsrfToken csrf) {
    Account member = member(accountId);
    String administrator = actors.actor(authentication).accountId().toString();
    Map<ToolKey, Decision> decisions = new LinkedHashMap<>();
    access.decisions(administrator, accountId).forEach(value -> decisions.put(value.key(), value));
    Map<ToolKey, ToolRow> rows = new LinkedHashMap<>();
    access
        .availableTools(administrator)
        .forEach(
            tool -> {
              var key = tool.key();
              var decision = decisions.get(key);
              var effective = access.effective(administrator, accountId, key);
              rows.put(
                  key,
                  new ToolRow(
                      key,
                      tool.providerName(),
                      tool.description(),
                      effective.allowed() && member.enabled() && tool.eligible(),
                      tool.eligible(),
                      true,
                      decision == null ? null : decision.changedAt().toString(),
                      effective.source(),
                      effective.override(),
                      effective.membersAllowed()));
            });
    decisions.forEach(
        (key, decision) ->
            rows.putIfAbsent(
                key,
                new ToolRow(
                    key,
                    key.providerId(),
                    "This provider is stopped or not loaded. Saved access applies when it becomes available.",
                    decision.allowed(),
                    false,
                    false,
                    decision.changedAt().toString(),
                    "User override",
                    decision.allowed() ? "ALLOW" : "DENY",
                    false)));
    access
        .defaults(administrator)
        .forEach(
            value -> {
              if (!rows.containsKey(value.key())) {
                var effective = access.effective(administrator, accountId, value.key());
                rows.put(
                    value.key(),
                    new ToolRow(
                        value.key(),
                        value.key().providerId(),
                        "Provider unavailable; saved access applies when started.",
                        effective.allowed() && member.enabled(),
                        false,
                        false,
                        value.changedAt().toString(),
                        effective.source(),
                        effective.override(),
                        effective.membersAllowed()));
              }
            });
    model.addAttribute("member", member.loginName());
    model.addAttribute("accountId", accountId);
    model.addAttribute(
        "tools",
        rows.values().stream()
            .sorted(
                Comparator.comparing(
                    value ->
                        value.key().moduleId()
                            + ":"
                            + value.key().providerId()
                            + ":"
                            + value.key().toolName()))
            .toList());
    model.addAttribute("csrf", csrf);
    return "accounts/tools";
  }

  @PostMapping("/zalava/accounts/{accountId}/tools")
  String decide(
      @PathVariable String accountId,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam(required = false) Boolean allowed,
      @RequestParam(required = false) String decision,
      Authentication authentication,
      RedirectAttributes redirect) {
    member(accountId);
    try {
      String administrator = actors.actor(authentication).accountId().toString();
      var key = new ToolKey(moduleId, providerId, toolName);
      if ("INHERIT".equals(decision)) access.inherit(administrator, accountId, key);
      else {
        if (decision != null && !List.of("ALLOW", "DENY").contains(decision))
          throw new IllegalArgumentException("Invalid decision");
        if (decision == null && allowed == null)
          throw new IllegalArgumentException("Missing decision");
        access.decide(
            administrator, accountId, key, decision == null ? allowed : decision.equals("ALLOW"));
      }
      redirect.addFlashAttribute(
          "message",
          "INHERIT".equals(decision)
              ? "Role default restored."
              : ("ALLOW".equals(decision) || Boolean.TRUE.equals(allowed))
                  ? "Tool access granted."
                  : "Tool access revoked. Future calls are denied.");
    } catch (IllegalArgumentException invalid) {
      redirect.addFlashAttribute(
          "error",
          "Tool access could not be changed. The tool may be unavailable or restricted. Refresh and try again.");
    }
    return "redirect:/zalava/accounts/" + accountId + "/tools";
  }

  @GetMapping("/zalava/accounts/tool-defaults")
  String defaults(Authentication authentication, Model model, CsrfToken csrf) {
    String admin = actors.actor(authentication).accountId().toString();
    Map<ToolKey, Decision> defaults = new LinkedHashMap<>();
    access.defaults(admin).forEach(value -> defaults.put(value.key(), value));
    Map<ToolKey, ToolRow> rows = new LinkedHashMap<>();
    access
        .availableTools(admin)
        .forEach(
            tool -> {
              var key = tool.key();
              var value = defaults.get(key);
              rows.put(
                  key,
                  new ToolRow(
                      key,
                      tool.providerName(),
                      tool.description(),
                      value != null && value.allowed(),
                      tool.eligible(),
                      true,
                      value == null ? null : value.changedAt().toString(),
                      "Role default",
                      "INHERIT",
                      value != null && value.allowed()));
            });
    defaults.forEach(
        (key, value) ->
            rows.putIfAbsent(
                key,
                new ToolRow(
                    key,
                    key.providerId(),
                    "Provider unavailable",
                    value.allowed(),
                    false,
                    false,
                    value.changedAt().toString(),
                    "Role default",
                    "INHERIT",
                    value.allowed())));
    model.addAttribute(
        "tools",
        rows.values().stream()
            .sorted(
                Comparator.comparing(
                    value -> value.key().providerId() + ":" + value.key().toolName()))
            .toList());
    model.addAttribute("csrf", csrf);
    return "accounts/tool-defaults";
  }

  @PostMapping("/zalava/accounts/tool-defaults")
  String changeDefault(
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam boolean membersAllowed,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      access.defaultAccess(
          actors.actor(authentication).accountId().toString(),
          new ToolKey(moduleId, providerId, toolName),
          membersAllowed);
      redirect.addFlashAttribute(
          "message", "Role default saved. Explicit user overrides are retained.");
    } catch (IllegalArgumentException invalid) {
      redirect.addFlashAttribute(
          "error", "Tool default could not be changed. The tool may be unavailable or restricted.");
    }
    return "redirect:/zalava/accounts/tool-defaults";
  }

  private Account member(String accountId) {
    try {
      return accounts
          .findById(new AccountId(UUID.fromString(accountId)))
          .filter(value -> value.role() == AccountRole.MEMBER)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  public record ToolRow(
      ToolKey key,
      String providerName,
      String description,
      boolean allowed,
      boolean eligible,
      boolean available,
      String changedAt,
      String source,
      String override,
      boolean membersAllowed) {}
}
