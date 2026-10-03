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

  @org.springframework.beans.factory.annotation.Autowired
  private org.zalava.identity.accounts.application.port.in.AccountLifecycle accounts;

  @org.springframework.beans.factory.annotation.Autowired
  private org.zalava.identity.channels.application.port.in.ChannelIdentityLinks links;

  @org.springframework.beans.factory.annotation.Autowired
  private org.zalava.assistant.conversation.application.port.in.ConversationContinuation
      continuation;

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.beans.factory.annotation.Qualifier("actorConversations")
  private org.zalava.assistant.conversation.application.port.in.ActorConversations conversations;

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

        var actor =
            new org.zalava.identity.accounts.domain.Actor(
                accounts.findByLoginName(LOGIN).orElseThrow().id());
        var identity =
            new org.zalava.identity.channels.domain.ExternalChannelIdentity(
                "telegram", "browser-continuation");
        var link =
            links.link(
                actor,
                identity,
                org.zalava.identity.channels.domain.ChannelOperationScope.of("chat:send"));
        var source =
            continuation.channelConversation(
                actor,
                new org.zalava.assistant.conversation.domain.ConversationOrigin(
                    "telegram", identity.subject(), "private-browser-chat", true));
        var sourceMessages =
            List.of(
                new org.zalava.assistant.conversation.domain.ConversationMessage(
                    org.zalava.assistant.conversation.domain.ConversationMessage.Role.USER,
                    "Channel source question"),
                new org.zalava.assistant.conversation.domain.ConversationMessage(
                    org.zalava.assistant.conversation.domain.ConversationMessage.Role.ASSISTANT,
                    "Channel source answer"));
        conversations.saveAll(actor, source, sourceMessages);
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator("select[aria-label='Select conversation']").selectOption(source.value());
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("telegram history"))
            .waitFor();
        assertThat(page.locator("#message").count()).isZero();
        page.locator("#continuation-destination").focus();
        page.locator("#continuation-destination").press("Tab");
        assertThat(page.evaluate("document.activeElement.textContent"))
            .isEqualTo("Continue conversation");
        page.keyboard().press("Enter");
        page.locator("#message").waitFor();
        String targetId = page.locator("select[aria-label='Select conversation']").inputValue();
        assertThat(targetId).isNotEqualTo(source.value());
        page.getByText("Channel source answer").waitFor();
        page.locator("#message").fill("what time is it now");
        page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Send"))
            .click();
        page.getByText("12:34:56Z").waitFor();
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator("select[aria-label='Select conversation']").selectOption(targetId);
        page.getByText("12:34:56Z").waitFor();
        assertThat(conversations.findByReference(actor, source)).isEqualTo(sourceMessages);
        var targetMessages =
            conversations.findByReference(
                actor,
                new org.zalava.assistant.conversation.domain.ConversationReference(targetId));
        assertThat(targetMessages)
            .extracting(org.zalava.assistant.conversation.domain.ConversationMessage::text)
            .contains("Channel source question", "Channel source answer", "what time is it now")
            .anySatisfy(text -> assertThat(text).contains("12:34:56Z"));
        for (int width : new int[] {375, 768, 1440}) {
          page.setViewportSize(width, 900);
          if (width < 1024) {
            var disclosure =
                page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Workspace details"));
            if (!"true".equals(disclosure.getAttribute("aria-expanded"))) disclosure.click();
          }
          page.getByRole(
                  com.microsoft.playwright.options.AriaRole.TAB,
                  new Page.GetByRoleOptions().setName("Tools"))
              .click();
          page.getByText("No pending permissions.").waitFor();
          page.getByRole(
                  com.microsoft.playwright.options.AriaRole.TAB,
                  new Page.GetByRoleOptions().setName("Tools"))
              .press("ArrowRight");
          assertThat(page.evaluate("document.activeElement.textContent")).isEqualTo("Context");

          page.getByRole(
                  com.microsoft.playwright.options.AriaRole.TAB,
                  new Page.GetByRoleOptions().setName("Context"))
              .click();
          page.getByText("Available knowledge sources").waitFor();
          assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
              .isEqualTo(true);
        }
        links.revoke(actor, link.id());
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator("select[aria-label='Select conversation']").selectOption(source.value());
        page.getByText("Continuation is unavailable for this channel identity or destination.")
            .waitFor();
        assertThat(
                page.getByRole(
                        com.microsoft.playwright.options.AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Continue conversation"))
                    .count())
            .isZero();
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
