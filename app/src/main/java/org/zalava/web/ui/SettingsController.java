package org.zalava.web.ui;

import static org.zalava.SeaConfiguration.AGENT_MD;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
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
import org.zalava.SupportedProvider;
import org.zalava.assistant.channels.application.port.in.TelegramConfiguration;
import org.zalava.assistant.channels.application.port.in.TelegramConfigurationStatus;
import org.zalava.assistant.channels.application.port.in.TelegramConfigurationUpdate;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.application.port.in.ChannelLinkChallenges;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.runtime.SeaRuntime;

@Controller
public class SettingsController {

  private final Resource workspace;
  private final Environment environment;
  private final SeaRuntime seaRuntime;
  private final FileSystemModuleConfigurationStore moduleConfigurationStore;
  private final TelegramConfiguration telegramConfiguration;
  private final ChannelIdentityLinks channelIdentityLinks;
  private final ChannelLinkChallenges channelLinkChallenges;
  private final AuthenticatedActorResolver actors;

  public SettingsController(
      @Value("${agent.workspace}") Resource workspace,
      Environment environment,
      SeaRuntime seaRuntime,
      FileSystemModuleConfigurationStore moduleConfigurationStore,
      TelegramConfiguration telegramConfiguration,
      ChannelIdentityLinks channelIdentityLinks,
      ChannelLinkChallenges channelLinkChallenges,
      AuthenticatedActorResolver actors) {
    this.workspace = workspace;
    this.environment = environment;
    this.seaRuntime = seaRuntime;
    this.moduleConfigurationStore = moduleConfigurationStore;
    this.telegramConfiguration = telegramConfiguration;
    this.channelIdentityLinks = channelIdentityLinks;
    this.channelLinkChallenges = channelLinkChallenges;
    this.actors = actors;
  }

  @PostMapping("/settings/channels/telegram")
  public String updateTelegram(
      @RequestParam(defaultValue = "false") boolean enabled,
      @RequestParam(required = false) String tokenReplacement,
      @RequestParam(required = false) String allowedUsername,
      RedirectAttributes redirectAttributes) {
    try {
      telegramConfiguration.update(
          new TelegramConfigurationUpdate(enabled, tokenReplacement, allowedUsername));
      redirectAttributes.addFlashAttribute(
          "settingsMessage",
          "Telegram channel configuration saved. Restart SEA to apply the change.");
    } catch (IllegalArgumentException ex) {
      redirectAttributes.addFlashAttribute("settingsError", ex.getMessage());
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to update Telegram channel configuration", ex);
    }
    return "redirect:/settings";
  }

  @GetMapping("/settings")
  public String settings(Model model, CsrfToken csrf, Authentication authentication) {
    model.addAttribute("model", buildModel(actors.actor(authentication)));
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
      var issued =
          channelLinkChallenges.issue(
              actors.actor(authentication), channel, ChannelOperationScope.of("chat:send"));
      redirectAttributes.addFlashAttribute("channelLinkCode", issued.code());
      redirectAttributes.addFlashAttribute("channelLinkExpiresAt", issued.expiresAt());
    } catch (IllegalArgumentException exception) {
      redirectAttributes.addFlashAttribute("settingsError", exception.getMessage());
    }
    return "redirect:/settings";
  }

  @PostMapping("/settings/instructions")
  public String updateInstructions(
      @RequestParam String instructions, RedirectAttributes redirectAttributes) {
    String normalized = instructions.strip();
    if (normalized.isEmpty()) {
      redirectAttributes.addFlashAttribute(
          "settingsError", "Workspace instructions cannot be empty.");
      return "redirect:/settings";
    }

    try {
      Files.writeString(
          workspace.createRelative(AGENT_MD).getFilePath(),
          normalized + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to update workspace instructions", ex);
    }

    redirectAttributes.addFlashAttribute("settingsMessage", "Workspace instructions updated.");
    return "redirect:/settings";
  }

  private SettingsModel buildModel(org.zalava.identity.accounts.domain.Actor actor) {
    String providerId = environment.getProperty("spring.ai.model.chat", "unknown");
    String providerLabel =
        SupportedProvider.from(providerId).map(SupportedProvider::label).orElse(providerId);
    return new SettingsModel(
        workspacePath(),
        providerLabel,
        readInstructions(),
        configurationHealth(),
        telegramStatus());
  }

  private TelegramConfigurationStatus telegramStatus() {
    try {
      return telegramConfiguration.status();
    } catch (IOException ex) {
      return new TelegramConfigurationStatus(false, false, null);
    }
  }

  private String workspacePath() {
    try {
      return workspace.getFilePath().toString();
    } catch (IOException ex) {
      return workspace.getDescription();
    }
  }

  private String readInstructions() {
    String privateInstructions = readFile(AGENT_MD);
    if (privateInstructions != null) {
      return privateInstructions;
    }
    String defaultInstructions = readFile("AGENT.md");
    return defaultInstructions == null ? "" : defaultInstructions;
  }

  private String readFile(String name) {
    try {
      Path path = workspace.createRelative(name).getFilePath();
      return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
    } catch (IOException ex) {
      return null;
    }
  }

  private List<ModuleConfigurationHealth> configurationHealth() {
    return seaRuntime.modules().stream()
        .filter(
            module ->
                module.configuration().jsonSchema().get("properties")
                        instanceof java.util.Map<?, ?> properties
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

  private static String configurationLabel(org.zalava.ModuleConfigurationStatus status) {
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
      List<ModuleConfigurationHealth> moduleConfigurationHealth,
      TelegramConfigurationStatus telegram) {}

  public record ModuleConfigurationHealth(String moduleId, String status) {}
}
