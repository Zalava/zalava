package org.zalava.assistant.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real-browser foundation for the SEA chat entry point. The only substituted boundary is the model:
 * the HTTP server, JTE assets, WebSocket transport and conversation persistence all run exactly as
 * they do in SEA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ChatControllerComponentTest.ChatModelTestConfiguration.class,
  BrowserChatAccountConfiguration.class
})
class BrowserChatAcceptanceTest {

  private static final Path WORKSPACE = workspace();
  static final String LOGIN = "browser-test-" + UUID.randomUUID();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> "BrowserTestPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void sendsAStreamingChatTurnAndKeepsItAfterReload() throws IOException {
    Path diagnosticDirectory = Files.createDirectories(Path.of("build", "browser-acceptance"));
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      try {
        Page page = context.newPage();
        List<String> outboundFrames = new CopyOnWriteArrayList<>();
        page.onWebSocket(socket -> socket.onFrameSent(frame -> outboundFrames.add(frame.text())));
        page.navigate("http://127.0.0.1:" + port + "/login");
        page.locator("input[name=username]").fill(LOGIN);
        page.locator("input[name=password]").fill("BrowserTestPassword-123");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in"))
            .click();
        assertThat(page.url()).endsWith("/account/password");
        page.locator("input[name=currentPassword]").fill("BrowserTestPassword-123");
        page.locator("input[name=replacementPassword]").fill("BrowserTestPassword-456");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Change password"))
            .click();
        page.navigate("http://127.0.0.1:" + port + "/chat");
        assertThat(page.url()).endsWith("/chat");
        page.locator("#message").waitFor();
        page.getByText("Connected").waitFor();
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        page.locator("#message").fill("what time is it now");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        assertThat(outboundFrames)
            .anySatisfy(frame -> assertThat(frame).contains("chat.send", "what time is it now"));

        page.getByText("12:34:56Z").waitFor();
        assertThat(page.locator("body").innerText()).contains("what time is it now", "12:34:56Z");

        page.reload();
        page.getByText("12:34:56Z").waitFor();
        assertThat(page.locator("body").innerText()).contains("what time is it now");

        String originalConversation =
            page.locator("select[aria-label='Select conversation']").inputValue();
        page.locator("#message").fill("first shell prompt");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        page.getByText("first shell prompt").waitFor();
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("New conversation"))
            .click();
        page.waitForFunction(
            "document.querySelector(\"select[aria-label='Select conversation']\").options.length === 2");
        String newConversation =
            page.locator("select[aria-label='Select conversation']").inputValue();
        assertThat(newConversation).isNotEqualTo(originalConversation);
        page.locator("select[aria-label='Select conversation']").selectOption(originalConversation);
        page.getByText("first shell prompt").waitFor();

        page.locator("#message").fill("unfinished draft");
        page.locator("#message").press("ArrowUp");
        assertThat(page.locator("#message").inputValue()).isEqualTo("first shell prompt");
        page.locator("#message").press("ArrowDown");
        assertThat(page.locator("#message").inputValue()).isEqualTo("unfinished draft");

        page.locator("#message").fill("render markdown");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        page.getByText("Bold result").waitFor();
        assertThat(page.locator("pre code").innerText()).contains("System.out.println");

        page.locator("#message").fill("slow response");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        page.getByText("Zalava is responding…").waitFor();
        page.getByText("Eventually complete.").waitFor();
        assertThat(page.getByText("Zalava is responding…").isHidden()).isTrue();

        page.locator("#message").fill("fail chat");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        page.getByRole(com.microsoft.playwright.options.AriaRole.ALERT)
            .getByText("SEA could not complete that request")
            .waitFor();
        assertThat(page.locator(".messages").getAttribute("aria-busy")).isEqualTo("false");

        page.setViewportSize(375, 667);
        assertThat(page.locator("select[aria-label='Select conversation']").isVisible()).isTrue();
        assertThat(page.locator("#message").isVisible()).isTrue();
        assertThat(
                page.getByRole(
                        com.microsoft.playwright.options.AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("New conversation"))
                    .isVisible())
            .isTrue();

        page.navigate("http://127.0.0.1:" + port + "/settings");
        page.locator("textarea[name=instructions]").fill("Browser acceptance instructions.");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save instructions"))
            .click();
        page.getByText("Workspace instructions updated.").waitFor();
        assertThat(page.locator("textarea[name=instructions]").inputValue())
            .isEqualTo("Browser acceptance instructions.\n");

        page.locator("textarea[name=instructions]").fill("   ");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save instructions"))
            .click();
        page.getByText("Workspace instructions cannot be empty.").waitFor();
        assertThat(page.locator("textarea[name=instructions]").inputValue())
            .isEqualTo("Browser acceptance instructions.\n");
      } finally {
        context
            .tracing()
            .stop(
                new Tracing.StopOptions()
                    .setPath(diagnosticDirectory.resolve("chat-tool-trace.zip")));
      }
    }
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-acceptance-");
      Files.writeString(root.resolve("AGENT.md"), "Browser acceptance test workspace.");
      Files.writeString(root.resolve("INFO.md"), "Disposable test environment.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
