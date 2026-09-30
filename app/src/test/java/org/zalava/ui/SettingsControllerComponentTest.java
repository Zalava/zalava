package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.SeaComponentTestInitializer;
import org.zalava.support.SecureMutableWorkspaceComponentTest;

@SecureMutableWorkspaceComponentTest
class SettingsControllerComponentTest {

  private static final Path WORKSPACE = SeaComponentTestInitializer.workspacePath();

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void resetInstructions() throws IOException {
    Files.writeString(WORKSPACE.resolve("AGENT.md"), "Default workspace instructions.");
    Files.deleteIfExists(WORKSPACE.resolve("AGENT.private.md"));
    Files.deleteIfExists(WORKSPACE.resolve("private/application.private.yaml"));
  }

  @Test
  void rendersSettingsWithActiveNavigationAndSafeDefaults() throws Exception {
    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Settings</title>")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "class=\"navbar-item is-active\" aria-current=\"page\" href=\"/settings\"")))
        .andExpect(content().string(containsString(WORKSPACE.toString())))
        .andExpect(content().string(containsString("OpenAI")))
        .andExpect(content().string(containsString("Default workspace instructions.")))
        .andExpect(content().string(containsString("Require your approval before execution.")))
        .andExpect(content().string(containsString("Pinned for consistent product readability.")));
  }

  @Test
  void rendersUnavailableTelegramConfigurationWithoutASecret() throws Exception {
    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Disabled. Enable it to receive messages through your Telegram bot.")))
        .andExpect(content().string(containsString("action=\"/settings/channels/telegram\"")))
        .andExpect(content().string(not(containsString("value=\"telegram-secret\""))));
  }

  @Test
  void enablesTelegramAndRequiresARestartWithoutRenderingItsToken() throws Exception {
    mockMvc
        .perform(
            post("/settings/channels/telegram")
                .param("enabled", "true")
                .param("tokenReplacement", "telegram-secret")
                .param("allowedUsername", "@operator"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(
            flash()
                .attribute(
                    "settingsMessage",
                    "Telegram channel configuration saved. Restart SEA to apply the change."));

    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Enabled for &#64;operator")))
        .andExpect(content().string(not(containsString("telegram-secret"))));
    org.assertj.core.api.Assertions.assertThat(
            Files.readString(WORKSPACE.resolve("private/application.private.yaml")))
        .contains("token: telegram-secret")
        .contains("username: operator");
  }

  @Test
  void preservesAnExistingTokenWhenOnlyTheAllowedUsernameChanges() throws Exception {
    Path configuration = WORKSPACE.resolve("private/application.private.yaml");
    Files.createDirectories(configuration.getParent());
    Files.writeString(
        configuration,
        """
                agent:
                  channels:
                    telegram:
                      token: telegram-secret
                      username: previous
                """);

    mockMvc
        .perform(
            post("/settings/channels/telegram")
                .param("enabled", "true")
                .param("tokenReplacement", "")
                .param("allowedUsername", "@next"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash()
                .attribute(
                    "settingsMessage",
                    "Telegram channel configuration saved. Restart SEA to apply the change."));

    org.assertj.core.api.Assertions.assertThat(Files.readString(configuration))
        .contains("token: telegram-secret")
        .contains("username: next");
  }

  @Test
  void rejectsEnabledTelegramWithoutCredentialsAndCanDisableIt() throws Exception {
    mockMvc
        .perform(
            post("/settings/channels/telegram")
                .param("enabled", "true")
                .param("tokenReplacement", "")
                .param("allowedUsername", ""))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash()
                .attribute("settingsError", "Enter the Telegram bot token to enable the channel."));

    mockMvc
        .perform(post("/settings/channels/telegram"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash()
                .attribute(
                    "settingsMessage",
                    "Telegram channel configuration saved. Restart SEA to apply the change."));

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(WORKSPACE.resolve("private/application.private.yaml")))
        .contains("token: false")
        .contains("username: false");
  }

  @Test
  void updatesPrivateWorkspaceInstructions() throws Exception {
    mockMvc
        .perform(
            post("/settings/instructions")
                .param("instructions", "  Updated workspace instructions.  "))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsMessage", "Workspace instructions updated."));

    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Updated workspace instructions.")))
        .andExpect(content().string(not(containsString("Default workspace instructions."))));

    org.assertj.core.api.Assertions.assertThat(
            Files.readString(WORKSPACE.resolve("AGENT.private.md")))
        .isEqualTo("Updated workspace instructions." + System.lineSeparator());
  }

  @Test
  void rejectsBlankInstructionsWithoutChangingWorkspaceFiles() throws Exception {
    mockMvc
        .perform(post("/settings/instructions").param("instructions", "   "))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsError", "Workspace instructions cannot be empty."));

    org.assertj.core.api.Assertions.assertThat(WORKSPACE.resolve("AGENT.private.md"))
        .doesNotExist();
    org.assertj.core.api.Assertions.assertThat(Files.readString(WORKSPACE.resolve("AGENT.md")))
        .isEqualTo("Default workspace instructions.");
  }
}
