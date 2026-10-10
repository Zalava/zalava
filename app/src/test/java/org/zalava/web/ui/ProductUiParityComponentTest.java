package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.AuthenticatedZalavaComponentTest;

@AuthenticatedZalavaComponentTest
class ProductUiParityComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Test
  void rootOpensChatAndPagesUseZalavaProductNavigation() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/chat"));

    assertProductPage("/dashboard", "Zalava Dashboard");
    assertInteractiveChatPage();
    assertProductPage("/jobs", "Zalava Jobs");
    assertProductPage("/modules", "Zalava Modules");
    assertProductPage("/settings", "Zalava Settings");
  }

  private void assertProductPage(String path, String title) throws Exception {
    mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>" + title + "</title>")))
        .andExpect(content().string(containsString("href=\"/dashboard\"")))
        .andExpect(content().string(containsString("href=\"/chat\"")))
        .andExpect(content().string(containsString("href=\"/jobs\"")))
        .andExpect(content().string(containsString("href=\"/modules\"")))
        .andExpect(content().string(containsString("href=\"/settings\"")))
        .andExpect(content().string(not(containsString("JobRunr Dashboard"))));
  }

  private void assertInteractiveChatPage() throws Exception {
    mockMvc
        .perform(get("/chat"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Chat</title>")))
        .andExpect(content().string(containsString("id=\"root\"")))
        .andExpect(content().string(containsString("/zalava-chat/assets/zalava-chat.js")))
        .andExpect(content().string(containsString("href=\"/jobs\"")));
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("product-ui-parity-component-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal skill for component test context startup.
                    ---

                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
