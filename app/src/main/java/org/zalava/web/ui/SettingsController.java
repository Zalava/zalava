package org.zalava.web.ui;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.api.ModuleConfigurationStatus;
import org.zalava.assistant.agent.WorkspaceInstructions;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog.Provider;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.identity.channels.application.port.in.ChannelLinkChallenges;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.runtime.ZalavaRuntime;

@Controller
public class SettingsController {

  private final Resource workspace;
  private final ModelProviderConfiguration providers;
  private final Environment environment;
  private final ZalavaRuntime zalavaRuntime;
  private final FileSystemModuleConfigurationStore moduleConfigurationStore;
  private final WorkspaceInstructions instructions;
  private final ChannelLinkChallenges channelLinkChallenges;
  private final AuthenticatedActorResolver actors;

  public SettingsController(
      @Value("${agent.workspace}") Resource workspace,
      Environment environment,
      ZalavaRuntime zalavaRuntime,
      FileSystemModuleConfigurationStore moduleConfigurationStore,
      WorkspaceInstructions instructions,
      ChannelLinkChallenges channelLinkChallenges,
      AuthenticatedActorResolver actors,
      ModelProviderConfiguration providers) {
    this.providers = providers;
    this.workspace = workspace;
    this.environment = environment;
    this.zalavaRuntime = zalavaRuntime;
    this.moduleConfigurationStore = moduleConfigurationStore;
    this.instructions = instructions;
    this.channelLinkChallenges = channelLinkChallenges;
    this.actors = actors;
  }

  @GetMapping("/settings")
  public String settings(
      Model model,
      CsrfToken csrf,
      @RequestParam(defaultValue = "assistant") String section,
      @RequestParam(required = false) String provider)
      throws IOException {
    model.addAttribute("providers", ChatProviderCatalog.providers());
    try {
      model.addAttribute("providerSettings", providers.display(provider));
    } catch (IllegalArgumentException invalid) {
      model.addAttribute("providerSettings", providers.display(null));
    }
    model.addAttribute("model", buildModel());
    model.addAttribute(
        "section",
        List.of("assistant", "provider", "modules", "channels", "permissions").contains(section)
            ? section
            : "assistant");
    model.addAttribute("csrf", csrf);
    if (!model.containsAttribute("channelLinkCode")) {
      model.addAttribute("channelLinkCode", null);
      model.addAttribute("channelLinkExpiresAt", null);
    }
    return "ui/settings";
  }

  @PostMapping("/settings/channel-links")
  public String issueChannelLink(
      @RequestParam String channel,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    try {
      if (!availableChannels().contains(channel))
        throw new IllegalArgumentException(
            "This channel is not available. Install and configure its module first.");
      var issued =
          channelLinkChallenges.issue(
              actors.actor(authentication), channel, ChannelOperationScope.of("chat:send"));
      redirectAttributes.addFlashAttribute("channelLinkCode", issued.code());
      redirectAttributes.addFlashAttribute("channelLinkExpiresAt", issued.expiresAt());
    } catch (IllegalArgumentException exception) {
      redirectAttributes.addFlashAttribute("settingsError", exception.getMessage());
    }
    return "redirect:/settings?section=channels";
  }

  @PostMapping("/settings/instructions")
  public String updateInstructions(
      @RequestParam String instructions, RedirectAttributes redirectAttributes) {
    try {
      this.instructions.save(instructions);
      redirectAttributes.addFlashAttribute("settingsMessage", "Workspace instructions updated.");
    } catch (IllegalArgumentException exception) {
      redirectAttributes.addFlashAttribute("settingsError", exception.getMessage());
    }
    return "redirect:/settings";
  }

  @PostMapping("/settings/instructions/reset")
  public String resetInstructions(RedirectAttributes redirectAttributes) {
    instructions.reset();
    redirectAttributes.addFlashAttribute(
        "settingsMessage", "Default workspace instructions restored.");
    return "redirect:/settings";
  }

  private SettingsModel buildModel() {
    String providerId = environment.getProperty("spring.ai.model.chat", "unknown");
    String providerLabel =
        ChatProviderCatalog.providers().stream()
            .filter(
                p -> p.id().equals(environment.getProperty("zalava.model.provider", providerId)))
            .map(Provider::label)
            .findFirst()
            .orElse("Not configured");
    return new SettingsModel(
        workspacePath(),
        providerLabel,
        instructions.current(),
        instructions.customized(),
        configurationHealth(),
        availableChannels());
  }

  private List<String> availableChannels() {
    return zalavaRuntime.activeModules().stream()
        .flatMap(module -> module.channels().stream())
        .map(channel -> channel.descriptor().channelId())
        .distinct()
        .sorted()
        .toList();
  }

  private String workspacePath() {
    try {
      return workspace.getFilePath().toString();
    } catch (IOException ex) {
      return workspace.getDescription();
    }
  }

  private List<ModuleConfigurationHealth> configurationHealth() {
    return zalavaRuntime.modules().stream()
        .filter(
            module ->
                module.configuration().jsonSchema().get("properties")
                        instanceof Map<?, ?> properties
                    && !properties.isEmpty())
        .map(
            module ->
                new ModuleConfigurationHealth(
                    module.descriptor().moduleId(),
                    configurationLabel(
                        moduleConfigurationStore.status(module.descriptor().moduleId()))))
        .sorted(Comparator.comparing(ModuleConfigurationHealth::moduleId))
        .toList();
  }

  private static String configurationLabel(ModuleConfigurationStatus status) {
    return switch (status) {
      case ACTIVE -> "Active";
      case RESTART_REQUIRED -> "Changes pending";
      case CONFIGURATION_INVALID -> "Needs recovery";
      case ACTIVATION_FAILED -> "Activation failed";
      case SETUP_REQUIRED -> "Setup required";
    };
  }

  public record SettingsModel(
      String workspacePath,
      String providerLabel,
      String instructions,
      boolean instructionsCustomized,
      List<ModuleConfigurationHealth> moduleConfigurationHealth,
      List<String> availableChannels) {}

  public record ModuleConfigurationHealth(String moduleId, String status) {}
}
