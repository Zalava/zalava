package org.zalava.web.ui;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration.FieldValidationException;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;

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
          "Provider configuration saved and applied. New requests use it immediately; connectivity has not been verified.");
    } catch (IllegalArgumentException exception) {
      preserveInput(provider, form, redirect);
      if (exception instanceof FieldValidationException invalid) {
        Map<String, String> errors = new LinkedHashMap<>();
        invalid.fields().forEach(field -> errors.put(field, invalid.getMessage()));
        redirect.addFlashAttribute("providerFieldErrors", errors);
      }
      redirect.addFlashAttribute("settingsError", exception.getMessage());
    } catch (IOException exception) {
      preserveInput(provider, form, redirect);
      redirect.addFlashAttribute(
          "settingsError",
          "Unable to save provider settings. Your previous configuration remains unchanged.");
    }
    return "redirect:/settings?section=provider";
  }

  private static void preserveInput(
      String provider, Map<String, String> form, RedirectAttributes redirect) {
    var selected =
        ChatProviderCatalog.providers().stream()
            .filter(item -> item.id().equals(provider))
            .findFirst();
    if (selected.isEmpty()) return;
    Map<String, String> values = new LinkedHashMap<>();
    for (var field : selected.get().fields()) {
      String value = form.get(field.name());
      if (!field.secret()
          && value != null
          && !(field.name().equals("baseUrl") && value.contains("@"))) {
        values.put(field.name(), value);
      }
    }
    redirect.addFlashAttribute("submittedProvider", provider);
    redirect.addFlashAttribute("providerValues", values);
  }
}
