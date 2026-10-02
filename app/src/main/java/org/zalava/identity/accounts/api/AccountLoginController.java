package org.zalava.identity.accounts.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
  String login(Model model, CsrfToken csrf) {
    model.addAttribute("csrf", csrf);
    return "accounts/login";
  }

  @GetMapping("/account/password")
  String password(Model model, CsrfToken csrf) {
    model.addAttribute("error", null);
    model.addAttribute("csrf", csrf);
    return "accounts/password";
  }

  @PostMapping("/account/password")
  String changePassword(
      Authentication authentication,
      @RequestParam String currentPassword,
      @RequestParam String replacementPassword,
      Model model,
      CsrfToken csrf) {
    try {
      var account = accounts.findByLoginName(authentication.getName()).orElseThrow();
      accounts.changePassword(account.id(), currentPassword, replacementPassword);
      return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
          ? "redirect:/sea/control"
          : "redirect:/dashboard";
    } catch (RuntimeException exception) {
      model.addAttribute("error", "Password could not be changed");
      model.addAttribute("csrf", csrf);
      return "accounts/password";
    }
  }
}
