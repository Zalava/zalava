package org.zalava.web.ui;

import java.util.Comparator;
import java.util.LinkedHashMap;
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
import org.zalava.capabilities.operation.application.MemberProviderCapabilityPolicy;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.Decision;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

@Controller
public class MemberToolAccessController {
  private final MemberToolAccess access;
  private final AccountLifecycle accounts;
  private final AuthenticatedActorResolver actors;
  private final RuntimeQueries runtime;

  public MemberToolAccessController(
      MemberToolAccess access,
      AccountLifecycle accounts,
      AuthenticatedActorResolver actors,
      RuntimeQueries runtime) {
    this.access = access;
    this.accounts = accounts;
    this.actors = actors;
    this.runtime = runtime;
  }

  @GetMapping("/zalava/accounts/{accountId}/tools")
  String tools(
      @PathVariable String accountId, Authentication authentication, Model model, CsrfToken csrf) {
    Account member = member(accountId);
    String administrator = actors.actor(authentication).accountId().toString();
    Map<ToolKey, Decision> decisions = new LinkedHashMap<>();
    access.decisions(administrator, accountId).forEach(value -> decisions.put(value.key(), value));
    Map<ToolKey, ToolRow> rows = new LinkedHashMap<>();
    runtime
        .providers()
        .forEach(
            provider ->
                provider
                    .listTools()
                    .forEach(
                        tool -> {
                          var key =
                              new ToolKey(
                                  provider.descriptor().moduleId(),
                                  provider.descriptor().providerId(),
                                  tool.name());
                          Decision decision = decisions.get(key);
                          rows.put(
                              key,
                              new ToolRow(
                                  key,
                                  provider.descriptor().displayName(),
                                  tool.description(),
                                  decision != null && decision.allowed(),
                                  MemberProviderCapabilityPolicy.eligible(provider, tool),
                                  true,
                                  decision == null ? null : decision.changedAt().toString()));
                        }));
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
                    decision.changedAt().toString())));
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
      @RequestParam boolean allowed,
      Authentication authentication,
      RedirectAttributes redirect) {
    member(accountId);
    try {
      access.decide(
          actors.actor(authentication).accountId().toString(),
          accountId,
          new ToolKey(moduleId, providerId, toolName),
          allowed);
      redirect.addFlashAttribute(
          "message",
          allowed ? "Tool access granted." : "Tool access revoked. Future calls are denied.");
    } catch (IllegalArgumentException invalid) {
      redirect.addFlashAttribute(
          "error",
          "Tool access could not be changed. The tool may be unavailable or restricted. Refresh and try again.");
    }
    return "redirect:/zalava/accounts/" + accountId + "/tools";
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
      String changedAt) {}
}
