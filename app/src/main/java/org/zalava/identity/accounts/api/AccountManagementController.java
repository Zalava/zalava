package org.zalava.identity.accounts.api;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;

/** Administrator-only HTML adapter for managed Zalava group accounts. */
@Controller
@RequestMapping("/zalava/accounts")
public class AccountManagementController {
  private static final Set<String> SAFE_ERRORS =
      Set.of(
          "Login name is already in use", "Login name must contain between 1 and 100 characters",
          "Login name contains unsupported characters",
              "Password must contain between 12 and 256 characters",
          "At least one enabled administrator is required", "Account does not exist");
  private final AccountLifecycle accounts;

  public AccountManagementController(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  @GetMapping
  String accounts(@RequestParam(required = false) String error, Model model, CsrfToken csrf) {
    model.addAttribute("accounts", accounts.list().stream().map(ManagedAccountView::from).toList());
    if (error != null && !model.containsAttribute("error")) {
      model.addAttribute("error", "Account change could not be completed. Please try again.");
    }
    model.addAttribute("csrf", csrf);
    return "accounts/manage";
  }

  @PostMapping("/create")
  String create(
      @RequestParam String loginName,
      @RequestParam String temporaryPassword,
      @RequestParam AccountRole role,
      RedirectAttributes redirect) {
    try {
      accounts.create(loginName, temporaryPassword, role);
      redirect.addFlashAttribute(
          "message",
          "Account created. The member must change their temporary password at sign-in.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", safeError(exception));
      redirect.addFlashAttribute("createLogin", loginName);
      redirect.addFlashAttribute("createRole", role);
    }
    return "redirect:/zalava/accounts";
  }

  @PostMapping("/role")
  String role(
      @RequestParam String accountId, @RequestParam AccountRole role, RedirectAttributes redirect) {
    try {
      accounts.setRole(accountId(accountId), role);
      redirect.addFlashAttribute("message", "Account role updated.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", safeError(exception));
    }
    return "redirect:/zalava/accounts";
  }

  @PostMapping("/enabled")
  String enabled(
      @RequestParam String accountId, @RequestParam boolean enabled, RedirectAttributes redirect) {
    try {
      accounts.setEnabled(accountId(accountId), enabled);
      redirect.addFlashAttribute(
          "message",
          enabled
              ? "Account enabled."
              : "Account disabled. Existing sessions no longer have access.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", safeError(exception));
    }
    return "redirect:/zalava/accounts";
  }

  @PostMapping("/password")
  String resetPassword(
      @RequestParam String accountId,
      @RequestParam String temporaryPassword,
      RedirectAttributes redirect) {
    try {
      accounts.resetPassword(accountId(accountId), temporaryPassword);
      redirect.addFlashAttribute(
          "message",
          "Password reset. The account must change its temporary password at the next sign-in.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", safeError(exception));
    }
    return "redirect:/zalava/accounts";
  }

  private static String safeError(RuntimeException exception) {
    String message = exception.getMessage();
    return message != null && SAFE_ERRORS.contains(message)
        ? message
        : "Account change could not be completed. Please try again.";
  }

  private static AccountId accountId(String value) {
    return new AccountId(UUID.fromString(value));
  }
}
