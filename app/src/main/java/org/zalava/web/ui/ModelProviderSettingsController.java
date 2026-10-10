package org.zalava.web.ui;

import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;

@Controller
public final class ModelProviderSettingsController {
  private final ModelProviderConfiguration configuration;

  public ModelProviderSettingsController(ModelProviderConfiguration configuration) {
    this.configuration = configuration;
  }

  @GetMapping({"/onboarding", "/onboarding/", "/onboarding/{step}"})
  public String retiredWizard() {
    return "redirect:/settings?section=provider";
  }

  @PostMapping("/settings/provider")
  public String save(
      @RequestParam String provider,
      @RequestParam Map<String, String> form,
      RedirectAttributes redirect) {
    try {
      configuration.save(provider, form);
      redirect.addFlashAttribute(
          "settingsMessage",
          "Provider configuration saved. Restart Zalava to apply it; connectivity has not been verified.");
    } catch (IllegalArgumentException exception) {
      redirect.addFlashAttribute("settingsError", exception.getMessage());
    } catch (IOException exception) {
      redirect.addFlashAttribute(
          "settingsError",
          "Unable to save provider settings. Your previous configuration remains unchanged.");
    }
    return "redirect:/settings?section=provider";
  }
}
