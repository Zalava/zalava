package org.zalava.accounts.api;

import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;

/** Administrator-only HTML adapter for managed SEA group accounts. */
@Controller
@RequestMapping("/sea/accounts")
public class AccountManagementController {
  private final AccountLifecycle accounts;

  public AccountManagementController(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  @GetMapping
  String accounts(@RequestParam(required = false) String error, Model model, CsrfToken csrf) {
    model.addAttribute("accounts", accounts.list().stream().map(ManagedAccountView::from).toList());
    model.addAttribute("error", error == null ? null : "Account change could not be completed");
    model.addAttribute("csrf", csrf);
    return "accounts/manage";
  }

  @PostMapping("/create")
  String create(
      @RequestParam String loginName,
      @RequestParam String temporaryPassword,
      @RequestParam AccountRole role) {
    try {
      accounts.create(loginName, temporaryPassword, role);
      return "redirect:/sea/accounts";
    } catch (RuntimeException exception) {
      return "redirect:/sea/accounts?error";
    }
  }

  @PostMapping("/role")
  String role(@RequestParam String accountId, @RequestParam AccountRole role) {
    try {
      accounts.setRole(accountId(accountId), role);
      return "redirect:/sea/accounts";
    } catch (RuntimeException exception) {
      return "redirect:/sea/accounts?error";
    }
  }

  @PostMapping("/enabled")
  String enabled(@RequestParam String accountId, @RequestParam boolean enabled) {
    try {
      accounts.setEnabled(accountId(accountId), enabled);
      return "redirect:/sea/accounts";
    } catch (RuntimeException exception) {
      return "redirect:/sea/accounts?error";
    }
  }

  @PostMapping("/password")
  String resetPassword(@RequestParam String accountId, @RequestParam String temporaryPassword) {
    try {
      accounts.resetPassword(accountId(accountId), temporaryPassword);
      return "redirect:/sea/accounts";
    } catch (RuntimeException exception) {
      return "redirect:/sea/accounts?error";
    }
  }

  private static AccountId accountId(String value) {
    return new AccountId(UUID.fromString(value));
  }
}
