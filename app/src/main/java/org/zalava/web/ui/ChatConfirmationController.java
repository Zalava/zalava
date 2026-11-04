package org.zalava.web.ui;

import java.util.Comparator;
import java.util.List;
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
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Effective;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Tool;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;

@Controller
public class ChatConfirmationController {
  private final ChatToolConfirmation policy;
  private final AccountLifecycle accounts;
  private final AuthenticatedActorResolver actors;

  public ChatConfirmationController(
      ChatToolConfirmation policy, AccountLifecycle accounts, AuthenticatedActorResolver actors) {
    this.policy = policy;
    this.accounts = accounts;
    this.actors = actors;
  }

  @GetMapping("/zalava/accounts/chat-confirmations")
  String page(
      @RequestParam(required = false) String accountId,
      Authentication authentication,
      Model model,
      CsrfToken csrf) {
    String admin = actors.actor(authentication).accountId().toString();
    List<AccountChoice> people =
        accounts.list().stream()
            .map(
                a ->
                    new AccountChoice(
                        a.id().toString(), a.loginName(), a.role().name(), a.enabled()))
            .toList();
    if (accountId != null && people.stream().noneMatch(a -> a.id().equals(accountId)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    List<Row> rows =
        policy.tools(admin).stream()
            .sorted(Comparator.comparing(t -> t.key().providerId() + ":" + t.key().toolName()))
            .map(
                t ->
                    new Row(
                        t,
                        policy.roleDefault(admin, AccountRole.ADMIN, t.key()),
                        policy.roleDefault(admin, AccountRole.MEMBER, t.key()),
                        accountId == null ? null : policy.effective(admin, accountId, t.key())))
            .toList();
    model.addAttribute("people", people);
    model.addAttribute("accountId", accountId);
    model.addAttribute("tools", rows);
    model.addAttribute("csrf", csrf);
    return "accounts/chat-confirmations";
  }

  @PostMapping("/zalava/accounts/chat-confirmations/role")
  String role(
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String role,
      @RequestParam String choice,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      policy.setRole(
          actors.actor(authentication).accountId().toString(),
          AccountRole.valueOf(role),
          new ToolKey(moduleId, providerId, toolName),
          Choice.valueOf(choice));
      redirect.addFlashAttribute("message", "Role confirmation default saved.");
    } catch (IllegalArgumentException invalid) {
      redirect.addFlashAttribute(
          "error", "Confirmation setting could not be saved. Refresh and try again.");
    }
    return "redirect:/zalava/accounts/chat-confirmations";
  }

  @PostMapping("/zalava/accounts/chat-confirmations/user")
  String user(
      @RequestParam String accountId,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String choice,
      Authentication authentication,
      RedirectAttributes redirect) {
    try {
      policy.setUser(
          actors.actor(authentication).accountId().toString(),
          accountId,
          new ToolKey(moduleId, providerId, toolName),
          Choice.valueOf(choice));
      redirect.addFlashAttribute("message", "Individual confirmation setting saved.");
    } catch (IllegalArgumentException invalid) {
      redirect.addFlashAttribute(
          "error", "Confirmation setting could not be saved. Refresh and try again.");
    }
    redirect.addAttribute("accountId", accountId);
    return "redirect:/zalava/accounts/chat-confirmations?accountId={accountId}";
  }

  public record AccountChoice(String id, String login, String role, boolean enabled) {}

  public record Row(Tool tool, Choice adminDefault, Choice memberDefault, Effective user) {}
}
