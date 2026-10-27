package org.zalava.assistant.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SameSiteAttribute;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationContinuation;
import org.zalava.assistant.conversation.domain.ConversationMessage;
import org.zalava.assistant.conversation.domain.ConversationOrigin;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

/**
 * Real-browser foundation for the Zalava chat entry point. The only substituted boundary is the
 * model: the HTTP server, JTE assets, WebSocket transport and conversation persistence all run
 * exactly as they do in Zalava.
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

  @Autowired private AccountLifecycle accounts;

  @Autowired private ChannelIdentityLinks links;

  @Autowired private ConversationContinuation continuation;

  @Autowired
  @Qualifier("actorConversations")
  private ActorConversations conversations;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> "BrowserTestPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "openai");
    registry.add("spring.ai.openai.api-key", () -> "local-scripted-model");
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
        var loginPage = page.navigate("http://127.0.0.1:" + port + "/login");
        assertThat(loginPage.headerValue("set-cookie")).contains("XSRF-TOKEN=", "SameSite=Lax");
        page.locator("input[name=username]").fill(LOGIN);
        page.locator("input[name=password]").fill("BrowserTestPassword-123");
        var loginResponse =
            page.waitForResponse(
                response ->
                    response.url().endsWith("/login") && response.request().method().equals("POST"),
                () ->
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"))
                        .click());
        assertThat(
                loginResponse
                    .headerValue("set-cookie")
                    .lines()
                    .filter(cookie -> cookie.startsWith("JSESSIONID=")))
            .singleElement()
            .asString()
            .contains("SameSite=Lax", "HttpOnly");
        assertThat(page.url()).endsWith("/account/password");
        page.locator("input[name=currentPassword]").fill("BrowserTestPassword-123");
        page.locator("input[name=replacementPassword]").fill("BrowserTestPassword-456");
        page.locator("input[name=passwordConfirmation]").fill("BrowserTestPassword-456");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
            .click();
        page.navigate("http://127.0.0.1:" + port + "/chat");
        assertThat(page.url()).endsWith("/chat");
        page.locator("#message").waitFor();
        page.getByText("Connected").waitFor();
        assertThat(context.cookies())
            .filteredOn(
                cookie -> cookie.name.equals("JSESSIONID") || cookie.name.equals("XSRF-TOKEN"))
            .hasSize(2)
            .allSatisfy(cookie -> assertThat(cookie.sameSite).isEqualTo(SameSiteAttribute.LAX));
        assertThat(context.cookies())
            .filteredOn(cookie -> cookie.name.equals("JSESSIONID"))
            .allSatisfy(cookie -> assertThat(cookie.httpOnly).isTrue());
        assertThat(context.cookies())
            .filteredOn(cookie -> cookie.name.equals("XSRF-TOKEN"))
            .allSatisfy(cookie -> assertThat(cookie.httpOnly).isFalse());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send"))
            .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator("#message").fill("what time is it now");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        assertThat(outboundFrames)
            .anySatisfy(frame -> assertThat(frame).contains("chat.send", "what time is it now"));

        page.locator(".messages .answer-content").getByText("12:34:56Z").waitFor();
        assertThat(page.locator("body").innerText()).contains("what time is it now", "12:34:56Z");

        page.reload();
        page.locator(".messages .answer-content").getByText("12:34:56Z").waitFor();
        assertThat(page.locator("body").innerText()).contains("what time is it now");

        String originalConversation =
            page.locator("select[aria-label='Select conversation']").inputValue();
        page.locator("#message").fill("first shell prompt");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.locator(".messages").getByText("first shell prompt").waitFor();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
            .click();
        page.waitForFunction(
            "document.querySelector(\"select[aria-label='Select conversation']\").options.length === 2");
        String newConversation =
            page.locator("select[aria-label='Select conversation']").inputValue();
        assertThat(newConversation).isNotEqualTo(originalConversation);
        page.locator("select[aria-label='Select conversation']").selectOption(originalConversation);
        page.locator(".messages").getByText("first shell prompt").waitFor();

        page.locator("#message").fill("unfinished draft");
        page.locator("#message").press("Alt+ArrowUp");
        assertThat(page.locator("#message").inputValue()).isEqualTo("first shell prompt");
        page.locator("#message").press("Alt+ArrowDown");
        assertThat(page.locator("#message").inputValue()).isEqualTo("unfinished draft");

        page.locator("#message").fill("render markdown");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.locator(".messages").getByText("Bold result").waitFor();
        assertThat(page.locator("pre code").innerText()).contains("System.out.println");

        page.locator("#message").fill("slow response");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.getByText("Zalava is responding…").waitFor();
        page.locator(".messages").getByText("Eventually complete.").waitFor();
        assertThat(page.getByText("Zalava is responding…").isHidden()).isTrue();

        page.locator("#message").fill("fail chat");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.getByRole(AriaRole.ALERT)
            .getByText("Zalava could not complete that request")
            .waitFor();
        assertThat(page.locator(".messages").getAttribute("aria-busy")).isEqualTo("false");

        page.setViewportSize(375, 667);
        assertThat(page.locator("select[aria-label='Select conversation']").isVisible()).isTrue();
        assertThat(page.locator("#message").isVisible()).isTrue();
        assertThat(
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
                    .isVisible())
            .isTrue();

        var actor = new Actor(accounts.findByLoginName(LOGIN).orElseThrow().id());
        var identity = new ExternalChannelIdentity("telegram", "browser-continuation");
        var link = links.link(actor, identity, ChannelOperationScope.of("chat:send"));
        var source =
            continuation.channelConversation(
                actor,
                new ConversationOrigin(
                    "telegram", identity.subject(), "private-browser-chat", true));
        var sourceMessages =
            List.of(
                new ConversationMessage(ConversationMessage.Role.USER, "Channel source question"),
                new ConversationMessage(
                    ConversationMessage.Role.ASSISTANT, "Channel source answer"));
        conversations.saveAll(actor, source, sourceMessages);
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator("select[aria-label='Select conversation']").selectOption(source.value());
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("telegram history"))
            .waitFor();
        PlaywrightAssertions.assertThat(page.locator("#message")).hasCount(0);
        page.locator("#continuation-destination").focus();
        page.locator("#continuation-destination").press("Tab");
        assertThat(page.evaluate("document.activeElement.textContent"))
            .isEqualTo("Continue conversation");
        page.keyboard().press("Enter");
        page.locator("#message").waitFor();
        String targetId = page.locator("select[aria-label='Select conversation']").inputValue();
        assertThat(targetId).isNotEqualTo(source.value());
        page.locator(".messages").getByText("Channel source answer").waitFor();
        page.locator("#message").fill("what time is it now");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.locator(".messages .answer-content").getByText("12:34:56Z").waitFor();
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator("select[aria-label='Select conversation']").selectOption(targetId);
        page.locator(".messages .answer-content").getByText("12:34:56Z").waitFor();
        assertThat(conversations.findByReference(actor, source)).isEqualTo(sourceMessages);
        var targetMessages =
            conversations.findByReference(actor, new ConversationReference(targetId));
        assertThat(targetMessages)
            .extracting(ConversationMessage::text)
            .contains("Channel source question", "Channel source answer", "what time is it now")
            .anySatisfy(text -> assertThat(text).contains("12:34:56Z"));
        for (int width : new int[] {375, 768, 1440}) {
          page.setViewportSize(width, 900);
          if (width < 1024) {
            var disclosure =
                page.getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Workspace details"));
            if (!"true".equals(disclosure.getAttribute("aria-expanded"))) disclosure.click();
          }
          page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Tools")).click();
          page.getByText("No pending permissions.").waitFor();
          page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Tools"))
              .press("ArrowRight");
          assertThat(page.evaluate("document.activeElement.textContent")).isEqualTo("Context");

          page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Context")).click();
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
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Continue conversation"))
                    .count())
            .isZero();
        page.navigate("http://127.0.0.1:" + port + "/settings");
        page.locator("textarea[name=instructions]").fill("Browser acceptance instructions.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save instructions"))
            .click();
        page.getByText("Workspace instructions updated.").waitFor();
        assertThat(page.locator("textarea[name=instructions]").inputValue())
            .isEqualTo("Browser acceptance instructions.\n");

        page.locator("textarea[name=instructions]").fill("   ");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save instructions"))
            .click();
        page.getByText("Workspace instructions cannot be empty.").waitFor();
        assertThat(page.locator("textarea[name=instructions]").inputValue()).isEqualTo("   ");
      } finally {
        context
            .tracing()
            .stop(
                new Tracing.StopOptions()
                    .setPath(diagnosticDirectory.resolve("chat-tool-trace.zip")));
      }
    }
  }

  @Test
  void userMarkdownHeadingsRemainVisible() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signInDisposableMember(page);
      page.locator("#message").fill("## A visible formatting heading\n\nA plain sentence.");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      page.locator(".message.user").last().waitFor();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/chat-heading.png"))
              .setFullPage(true));
      assertThat(page.locator(".message.user").last().innerText())
          .contains("A visible formatting heading", "A plain sentence.");
    }
  }

  @Test
  void firstTokenWaitIsVisibleInsideTheAssistantBubble() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signInDisposableMember(page);
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        settleChatNavigation(page);
        page.locator("#message").fill("slow response");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        page.getByText("Zalava is responding…", new Page.GetByTextOptions().setExact(true))
            .waitFor();
        page.locator(".message.assistant").last().scrollIntoViewIfNeeded();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/chat-first-token-" + width + ".png"))
                .setFullPage(true));
        assertThat(page.locator(".message.assistant").last().getByRole(AriaRole.STATUS).count())
            .isEqualTo(1);
        page.waitForFunction(
            "document.querySelector('.messages').getAttribute('aria-busy') === 'false'");
        assertThat(page.locator(".message.assistant").last().innerText())
            .contains("Eventually complete.");
        assertThat(page.locator(".message.assistant").last().getByRole(AriaRole.STATUS).count())
            .isZero();
      }
    }
  }

  @Test
  void memoryReviewLinkWorksEvenWhenTheModelDoesNotRepeatTheUrl() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signInDisposableMember(page);
      page.locator("#message").fill("propose a greeting memory");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      page.getByText(
              "A memory proposal is ready for your review.",
              new Page.GetByTextOptions().setExact(true))
          .waitFor();
      page.getByRole(
              AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Review 1 memory proposal").setExact(true))
          .click();
      page.getByRole(
              AriaRole.LINK, new Page.GetByRoleOptions().setName("Full proposal").setExact(true))
          .waitFor();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        settleChatNavigation(page);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/chat-memory-review-" + width + ".png"))
                .setFullPage(true));
        assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
            .isEqualTo(true);
      }
      String href =
          page.getByRole(
                  AriaRole.LINK,
                  new Page.GetByRoleOptions().setName("Full proposal").setExact(true))
              .getAttribute("href");
      page.getByRole(
              AriaRole.LINK, new Page.GetByRoleOptions().setName("Full proposal").setExact(true))
          .click();
      page.locator(href.substring(href.indexOf('#'))).waitFor();
      assertThat(page.locator(href.substring(href.indexOf('#'))).innerText())
          .contains("My preferred greeting is meadow-lamp.", "Awaiting your review");
      page.locator(href.substring(href.indexOf('#'))).locator("[data-action=approve]").click();
      page.getByText("Memory approved and stored.").waitFor();
      page.navigate("http://127.0.0.1:" + port + "/chat");
      page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
      assertThat(
              page.getByRole(
                      AriaRole.LINK,
                      new Page.GetByRoleOptions().setName("Full proposal").setExact(true))
                  .count())
          .isZero();
      page.locator("#message").fill("What greeting do I prefer?");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      // Wait for this turn's response, not an idle state that can precede dispatch.
      PlaywrightAssertions.assertThat(page.locator(".message.user").last())
          .containsText("What greeting do I prefer?");
      PlaywrightAssertions.assertThat(
              page.locator(".message.assistant").last().locator(".answer-content p"))
          .hasText("Hello from the component test model.");
      PlaywrightAssertions.assertThat(page.locator(".messages")).hasAttribute("aria-busy", "false");
      page.reload();
      page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
      // Connected acknowledges the transport; history is delivered in a later snapshot.
      PlaywrightAssertions.assertThat(page.locator(".message.user").last().locator("p"))
          .hasText(new String[] {"What greeting do I prefer?"});
    }
  }

  @Test
  void titlesDatesAndUserNamingReplaceIdsAndRestoreTheSelectedConversation() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signInDisposableMember(page);
      var selector =
          page.getByRole(
              AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("Select conversation"));
      page.waitForFunction(
          "() => document.querySelector('select[aria-label=\"Select conversation\"]')?.value !== ''");
      assertThat(selector.locator("option:checked").innerText()).contains("New conversation");
      page.locator("#message").fill("Plan the school trip for Tuesday.");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      page.getByText(
              "Hello from the component test model.", new Page.GetByTextOptions().setExact(true))
          .waitFor();
      page.waitForFunction(
          "() => document.querySelector('select[aria-label=\"Select conversation\"] option:checked').textContent.includes('school trip')");
      assertThat(selector.locator("option:checked").innerText()).containsPattern("[0-9]{4}");
      String firstId = selector.inputValue();
      page.locator(".conversation-options summary").click();
      page.getByLabel("Conversation name", new Page.GetByLabelOptions().setExact(true))
          .fill("School trip planning");
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save name").setExact(true))
          .click();
      page.waitForFunction(
          "() => document.querySelector('select[aria-label=\"Select conversation\"] option:checked').textContent.includes('School trip planning')");
      for (int width : new int[] {390, 768, 1440}) {
        page.setViewportSize(width, 900);
        settleChatNavigation(page);
        assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
            .isEqualTo(true);
        assertThat(
                page.getByLabel("Conversation name", new Page.GetByLabelOptions().setExact(true))
                    .boundingBox()
                    .width)
            .isGreaterThan(100);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of(
                        "build/browser-acceptance/chat-conversation-naming-" + width + ".png")));
      }
      page.getByRole(
              AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("New conversation").setExact(true))
          .click();
      page.waitForFunction(
          "oldId => document.querySelector('select[aria-label=\"Select conversation\"]').value !== oldId",
          firstId);
      page.locator("#message").fill("A different later conversation.");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      page.getByText(
              "Hello from the component test model.", new Page.GetByTextOptions().setExact(true))
          .waitFor();
      selector.selectOption(firstId);
      page.getByText(
              "Plan the school trip for Tuesday.", new Page.GetByTextOptions().setExact(true))
          .waitFor();
      page.reload();
      page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
      page.getByText(
              "Plan the school trip for Tuesday.", new Page.GetByTextOptions().setExact(true))
          .waitFor();
      assertThat(selector.inputValue()).isEqualTo(firstId);
      assertThat(selector.locator("option:checked").innerText())
          .contains("School trip planning")
          .doesNotContain(firstId);
    }
  }

  @Test
  void waitsForTheInitialSnapshotBeforeConversationActionsAndPreservesChannelSelection() {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      Actor actor = signInDisposableMember(page);
      var selector = page.locator("select[aria-label='Select conversation']");
      PlaywrightAssertions.assertThat(selector).hasValue(Pattern.compile(".+"));
      String remembered = selector.inputValue();
      var identity =
          new ExternalChannelIdentity("telegram", "delayed-snapshot-" + UUID.randomUUID());
      links.link(actor, identity, ChannelOperationScope.of("chat:send"));
      var source =
          continuation.channelConversation(
              actor,
              new ConversationOrigin("telegram", identity.subject(), "private-delayed-chat", true));
      conversations.saveAll(
          actor,
          source,
          List.of(
              new ConversationMessage(ConversationMessage.Role.USER, "Delayed channel question"),
              new ConversationMessage(
                  ConversationMessage.Role.ASSISTANT, "Delayed channel answer")));
      // Forward real server traffic, but hold snapshots to expose the startup window.
      page.addInitScript(
          """
        window.heldConversationSnapshots = [];
        window.holdConversationSnapshots = true;
        window.releaseConversationSnapshots = () => {
          window.holdConversationSnapshots = false;
          window.heldConversationSnapshots.splice(0).forEach(deliver => deliver());
        };
        const NativeWebSocket = window.WebSocket;
        window.WebSocket = class extends NativeWebSocket {
          set onmessage(handler) {
            super.onmessage = event => {
              if (window.holdConversationSnapshots && JSON.parse(event.data).type === 'conversation.snapshot') {
                window.heldConversationSnapshots.push(() => handler.call(this, event));
              } else handler.call(this, event);
            };
          }
          get onmessage() { return super.onmessage; }
        };
        """);
      page.reload();
      page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
      page.waitForFunction("window.heldConversationSnapshots.length > 0");
      PlaywrightAssertions.assertThat(selector).isDisabled();
      var create =
          page.getByRole(
              AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("New conversation").setExact(true));
      PlaywrightAssertions.assertThat(create).isDisabled();
      PlaywrightAssertions.assertThat(
              page.getByText(
                  "Loading your conversations…", new Page.GetByTextOptions().setExact(true)))
          .isVisible();
      for (int width : List.of(1440, 768, 390)) {
        page.setViewportSize(width, 900);
        settleChatNavigation(page);
        assertThat(page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
            .isEqualTo(true);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/chat-initial-loading-" + width + ".png"))
                .setFullPage(true));
      }
      page.evaluate("window.releaseConversationSnapshots()");
      PlaywrightAssertions.assertThat(selector).hasValue(remembered);
      PlaywrightAssertions.assertThat(selector).isEnabled();
      PlaywrightAssertions.assertThat(create).isEnabled();
      selector.selectOption(source.value());
      PlaywrightAssertions.assertThat(
              page.getByRole(
                  AriaRole.HEADING, new Page.GetByRoleOptions().setName("telegram history")))
          .isVisible();
      PlaywrightAssertions.assertThat(selector).hasValue(source.value());
      PlaywrightAssertions.assertThat(page.locator("#message")).hasCount(0);
      PlaywrightAssertions.assertThat(
              page.getByText("Delayed channel answer", new Page.GetByTextOptions().setExact(true)))
          .isVisible();
      for (int width : List.of(1440, 768, 390)) {
        page.setViewportSize(width, 900);
        settleChatNavigation(page);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of(
                        "build/browser-acceptance/chat-initial-channel-ready-" + width + ".png"))
                .setFullPage(true));
      }
    }
  }

  @Test
  void signsOutThroughTheCsrfProtectedFormAndInvalidatesTheSession() {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(1440, 900);
      signInDisposableMember(page);
      page.locator("form[action='/logout'] button").click();
      page.waitForURL("**/login?logout");
      page.navigate("http://127.0.0.1:" + port + "/chat");
      assertThat(page.url()).endsWith("/login");
    }
  }

  private void settleChatNavigation(Page page) {
    page.waitForFunction(
        """
        () => {
          const menu = document.querySelector('.navbar-menu');
          if (!menu || window.innerWidth > 1024) return true;
          const bounds = menu.getBoundingClientRect();
          return menu.classList.contains('is-active') ? Math.abs(bounds.left) < .5 : bounds.right <= .5;
        }
        """);
  }

  private Actor signInDisposableMember(Page page) {
    String login = "browser-chat-" + UUID.randomUUID();
    var account = accounts.create(login, "BrowserTestPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "BrowserTestPassword-123", "BrowserTestPassword-456");
    page.navigate("http://127.0.0.1:" + port + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill("BrowserTestPassword-456");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.waitForURL("**/chat");
    page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
    return new Actor(account.id());
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-acceptance-");
      Files.writeString(root.resolve("AGENT.md"), "Browser acceptance test workspace.");
      Files.writeString(root.resolve("INFO.md"), "Disposable test environment.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
