package org.zalava.identity.accounts.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;

@Controller
@RequestMapping
public class AccountLoginController {
  private final AccountLifecycle accounts;

  public AccountLoginController(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/login")
  String login(
      Model model,
      CsrfToken csrf,
      @RequestParam(required = false) String error,
      @RequestParam(required = false) String logout) {
    model.addAttribute("error", error != null);
    model.addAttribute("loggedOut", logout != null);
    model.addAttribute("csrf", csrf);
    return "accounts/login";
  }

  @GetMapping("/account/password")
  String password(Model model, CsrfToken csrf, Authentication authentication) {
    model.addAttribute("error", null);
    passwordModel(model, csrf, authentication);
    return "accounts/password";
  }

  @PostMapping("/account/password")
  String changePassword(
      Authentication authentication,
      @RequestParam String currentPassword,
      @RequestParam String replacementPassword,
      @RequestParam(defaultValue = "") String passwordConfirmation,
      Model model,
      CsrfToken csrf) {
    if (!replacementPassword.equals(passwordConfirmation)) {
      model.addAttribute("error", "Passwords do not match");
      passwordModel(model, csrf, authentication);
      return "accounts/password";
    }
    try {
      var account = accounts.findByLoginName(authentication.getName()).orElseThrow();
      accounts.changePassword(account.id(), currentPassword, replacementPassword);
      return "redirect:/chat";
    } catch (RuntimeException exception) {
      model.addAttribute("error", "Password could not be changed");
      passwordModel(model, csrf, authentication);
      return "accounts/password";
    }
  }

  private void passwordModel(Model model, CsrfToken csrf, Authentication authentication) {
    var account = accounts.findByLoginName(authentication.getName()).orElseThrow();
    model.addAttribute("csrf", csrf);
    model.addAttribute("required", account.passwordChangeRequired());
    model.addAttribute("loginName", account.loginName());
  }
}
