package org.zalava.assistant.chat.api;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatController {
  private final ChatProviderReadiness readiness;

  public ChatController(ChatProviderReadiness readiness) {
    this.readiness = readiness;
  }

  @GetMapping("/chat")
  public String chat(Model model, Authentication authentication) {
    model.addAttribute("providerConfigured", readiness.configured());
    model.addAttribute(
        "providerSetupAllowed",
        authentication == null
            || authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    return "chat";
  }
}
