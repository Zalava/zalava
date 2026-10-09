package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.core.io.UrlResource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.WorkspaceInstructions;
import org.zalava.assistant.agent.adapter.out.springai.WorkspaceAgentPrompt;
import org.zalava.support.SecureMutableWorkspaceComponentTest;
import org.zalava.support.ZalavaComponentTestInitializer;

@SecureMutableWorkspaceComponentTest
class SettingsControllerComponentTest {
  private static final Path WORKSPACE = ZalavaComponentTestInitializer.workspacePath();
  @Autowired MockMvc mockMvc;
  @Autowired WorkspaceInstructions instructions;
  @Autowired WorkspaceAgentPrompt prompt;

  @BeforeEach
  void resetInstructions() throws IOException {
    Files.writeString(WORKSPACE.resolve("AGENT.md"), "Existing workspace instructions.");
    Files.deleteIfExists(WORKSPACE.resolve("AGENT.private.md"));
  }

  @Test
  void rendersGroupedSettingsAndPreservesExistingCustomization() throws Exception {
    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Settings</title>")))
        .andExpect(content().string(containsString("Settings sections")))
        .andExpect(content().string(containsString("Existing workspace instructions.")))
        .andExpect(content().string(containsString("Customized")))
        .andExpect(content().string(containsString("Reset to defaults")))
        .andExpect(content().string(not(containsString("Bot token"))));
    mockMvc
        .perform(get("/settings").param("section", "provider"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(WORKSPACE.toString())))
        .andExpect(content().string(containsString("OpenAI")));
    mockMvc
        .perform(get("/settings").param("section", "permissions"))
        .andExpect(content().string(containsString("Require your approval before execution.")));
    mockMvc
        .perform(get("/settings").param("section", "unknown"))
        .andExpect(content().string(containsString("Workspace instructions")));
  }

  @Test
  void moduleFreeHostDoesNotAdvertiseOrConfigureAbsentTelegram() throws Exception {
    mockMvc
        .perform(get("/settings").param("section", "channels"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No active channel modules are available.")))
        .andExpect(content().string(not(containsString("/settings/channel-links"))))
        .andExpect(content().string(not(containsString("Bot token"))));
    mockMvc
        .perform(post("/settings/channels/telegram").param("tokenReplacement", "unused-secret"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post("/settings/channel-links").param("channel", "telegram"))
        .andExpect(redirectedUrl("/settings?section=channels"))
        .andExpect(
            flash()
                .attribute(
                    "settingsError",
                    "This channel is not available. Install and configure its module first."))
        .andExpect(flash().attributeCount(1));
  }

  @Test
  void usesProductDefaultsWhenThereAreNoWorkspaceInstructions() throws Exception {
    Files.delete(WORKSPACE.resolve("AGENT.md"));
    mockMvc
        .perform(get("/settings"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("You are Zalava, the assistant for this workspace.")))
        .andExpect(content().string(containsString(">Default</span>")));
    assertThat(prompt.text()).contains(instructions.defaults());
  }

  @Test
  void savesInstructionsAndResetPersistsDefaultsWithoutRevivingLegacyContent() throws Exception {
    mockMvc
        .perform(post("/settings/instructions").param("instructions", "  Customized behavior.  "))
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsMessage", "Workspace instructions updated."));
    assertThat(Files.readString(WORKSPACE.resolve("AGENT.private.md")))
        .isEqualTo("Customized behavior." + System.lineSeparator());
    assertThat(prompt.text()).contains("Customized behavior.");
    mockMvc
        .perform(post("/settings/instructions/reset"))
        .andExpect(redirectedUrl("/settings"))
        .andExpect(
            flash().attribute("settingsMessage", "Default workspace instructions restored."));
    assertThat(
            new WorkspaceInstructions(new UrlResource(WORKSPACE.toUri() + "/")).current().strip())
        .isEqualTo(instructions.defaults());
    assertThat(instructions.customized()).isFalse();
    assertThat(prompt.text())
        .contains(instructions.defaults())
        .doesNotContain("Existing workspace instructions.");
    mockMvc
        .perform(get("/settings"))
        .andExpect(content().string(containsString(">Default</span>")));
  }

  @Test
  void rejectsBlankInstructionsWithoutChangingSavedContent() throws Exception {
    mockMvc
        .perform(post("/settings/instructions").param("instructions", "   "))
        .andExpect(redirectedUrl("/settings"))
        .andExpect(flash().attribute("settingsError", "Workspace instructions cannot be empty."));
    assertThat(WORKSPACE.resolve("AGENT.private.md")).doesNotExist();
    assertThat(instructions.current()).isEqualTo("Existing workspace instructions.");
  }
}
