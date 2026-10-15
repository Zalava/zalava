package org.zalava.web.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import reactor.core.publisher.Flux;

/** Real host/browser regressions for shared UI states and actor-owned library actions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BrowserUiStandardsAcceptanceTest.ModelConfiguration.class)
class BrowserUiStandardsAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String PASSWORD = "UiStandardsTestPassword-123";
  @LocalServerPort private int port;
  @Autowired private AccountLifecycle accounts;
  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "openai");
    registry.add("spring.ai.openai.api-key", () -> "scripted-ui-model");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
  }

  @BeforeEach
  void signIn() {
    String login = "ui-standards-" + UUID.randomUUID();
    var account = accounts.create(login, PASSWORD, AccountRole.ADMIN);
    accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    context = browser.newContext();
    page = context.newPage();
    page.navigate(base() + "/login");
    page.locator("#username").fill(login);
    page.locator("#password").fill(PASSWORD + "-changed");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
  }

  @AfterEach
  void close() {
    if (context != null) context.close();
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Test
  void secondaryHoverHasReadableContrastAndSharedTokens() {
    page.setViewportSize(1440, 900);
    page.navigate(base() + "/chat");
    var tools = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Tools"));
    tools.hover();
    page.waitForFunction(
        """
        () => document.getAnimations().every(animation => animation.playState !== 'running')
        """);
    double contrast =
        ((Number)
                tools.evaluate(
                    """
        el => {
          const luminance = color => {
            const rgb = color.match(/[\\d.]+/g).slice(0, 3).map(Number).map(v => v / 255)
              .map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
            return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
          };
          const style = getComputedStyle(el);
          const a = luminance(style.color), b = luminance(style.backgroundColor);
          return (Math.max(a,b) + .05) / (Math.min(a,b) + .05);
        }
        """))
            .doubleValue();
    assertThat(contrast).isGreaterThanOrEqualTo(4.5);
    page.navigate(base() + "/knowledge");
    assertThat(
            page.evaluate(
                "getComputedStyle(document.documentElement).getPropertyValue('--zalava-color-primary').trim()"))
        .isEqualTo("#1959a6");
  }

  @Test
  void multilineEditingUsesAnExplicitHistoryShortcutAndReconnectRetainsDraft() {
    page.addInitScript(
        """
        window.reviewSockets = [];
        const NativeSocket = window.WebSocket;
        window.WebSocket = class extends NativeSocket {
          constructor(...args) { super(...args); window.reviewSockets.push(this); }
        };
        """);
    page.navigate(base() + "/chat");
    var message = page.locator("#message");
    message.fill("History seed");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
        .click();
    assertThat(page.locator(".message.assistant")).containsText("Scripted UI reply");
    message.fill("First line\nSecond line");
    message.press("ArrowUp");
    assertThat(message).hasValue("First line\nSecond line");
    message.press("Alt+ArrowUp");
    assertThat(message).hasValue("History seed");
    message.press("Alt+ArrowDown");
    assertThat(message).hasValue("First line\nSecond line");
    page.evaluate("window.reviewSockets.forEach(socket => socket.close())");
    assertThat(page.locator(".zalava-status")).containsText("Disconnected");
    assertThat(page.locator(".zalava-status")).hasAttribute("data-state", "error");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reconnect").setExact(true))
        .click();
    assertThat(page.locator(".zalava-status")).containsText("Connected");
    assertThat(message).hasValue("First line\nSecond line");
  }

  @Test
  void memorySubmitDisablesTheImplicitSubmitterAndDeletionRequiresASecondAction() {
    page.navigate(base() + "/memory");
    page.locator("#proposal-text").fill("Disposable standards preference");
    page.evaluate(
        """
        document.addEventListener('submit', function (event) {
          sessionStorage.setItem('review-submit-disabled', String(event.target.querySelector('[data-action=propose]').disabled));
        });
        """);
    page.locator("[data-action=propose]").click();
    assertThat(page.getByText("Memory proposed for review.")).isVisible();
    assertThat(page.evaluate("sessionStorage.getItem('review-submit-disabled')")).isEqualTo("true");
    page.locator("[data-action=approve]").first().click();
    assertThat(page.getByText("Memory approved and stored.")).isVisible();
    var entry = page.locator("[data-memory-entry]").first();
    entry.locator("[data-action=delete]").click();
    assertThat(entry).containsText("Disposable standards preference");
    entry
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Cancel").setExact(true))
        .click();
    assertThat(entry.locator(".destructive-confirmation")).not().hasAttribute("open", "");
    entry.locator("[data-action=delete]").click();
    entry.locator("[data-action=confirm-delete]").click();
    assertThat(page.getByText("Memory deleted.")).isVisible();
    assertThat(page.locator("[data-memory-entry]")).hasCount(0);
  }

  @Test
  void knowledgeHasSourceActionsConfirmationAndUsefulNoResults() {
    page.navigate(base() + "/knowledge?query=does-not-exist");
    assertThat(page.getByText("No matching sources.", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    page.getByRole(
            AriaRole.LINK, new Page.GetByRoleOptions().setName("Clear filters").setExact(true))
        .click();
    page.locator("#knowledge-file")
        .setInputFiles(
            new FilePayload(
                "standards-review.txt",
                "text/plain",
                "Review fixture".getBytes(StandardCharsets.UTF_8)));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload").setExact(true))
        .click();
    page.getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions().setName("standards-review.txt").setExact(true))
        .click();
    assertThat(page.locator("h1")).hasText("standards-review.txt");
    assertThat(page.locator("[data-source-actions]")).isVisible();
    assertThat(page.locator("[data-action=delete]").boundingBox().height)
        .isEqualTo(page.locator("[data-source-actions] button").first().boundingBox().height);
    page.locator("[data-action=delete]").click();
    assertThat(page.locator("[data-action=confirm-delete]")).isVisible();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel").setExact(true))
        .click();
    page.reload();
    assertThat(page.locator("h1")).hasText("standards-review.txt");
    page.locator("[data-action=delete]").click();
    page.locator("[data-action=confirm-delete]").click();
    assertThat(page.getByText("Source deleted.", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
  }

  @Test
  void externalSubmitterAndJobCardsUseSharedFeedbackAndSpacing() {
    page.navigate(base() + "/settings");
    page.evaluate(
        """
        document.addEventListener('submit', function () {
          sessionStorage.setItem('review-external-disabled', String(document.querySelector('[form="instructions-form"]').disabled));
        });
        """);
    page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Save instructions").setExact(true))
        .click();
    assertThat(page.getByText("Workspace instructions updated.")).isVisible();
    assertThat(page.evaluate("sessionStorage.getItem('review-external-disabled')"))
        .isEqualTo("true");
    page.navigate(base() + "/jobs");
    assertThat(
            ((Number)
                    page.locator(".job-evidence section")
                        .first()
                        .evaluate("el => parseFloat(getComputedStyle(el).paddingLeft)"))
                .doubleValue())
        .isGreaterThanOrEqualTo(16);
  }

  @Test
  void conversationCommandsShowPendingAndConfirmedOutcomes() {
    page.addInitScript(
        """
        const NativeSocket = window.WebSocket;
        window.WebSocket = class extends NativeSocket {
          send(data) {
            const type = JSON.parse(data).type;
            if (type === 'chat.create' || type === 'chat.rename') {
              setTimeout(() => super.send(data), 1000);
            } else super.send(data);
          }
        };
        """);
    page.navigate(base() + "/chat");
    var create =
        page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("New conversation").setExact(true));
    assertThat(create).isEnabled();
    create.click();
    assertThat(page.getByText("Creating conversation…", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    assertThat(create).isDisabled();
    assertThat(page.getByText("Conversation created.", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    page.locator(".conversation-options summary").click();
    page.locator("#conversation-name").fill("UI confirmation test");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save name").setExact(true))
        .click();
    assertThat(
            page.getByText("Saving conversation name…", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    assertThat(page.locator("#conversation-name")).isDisabled();
    assertThat(
            page.getByText("Conversation name saved.", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    assertThat(page.locator(".conversations select")).containsText("UI confirmation test");
  }

  @Test
  void anUnacknowledgedConversationChangeOffersRecoveryWithoutLosingTheDraft() throws Exception {
    page.addInitScript(
        """
        const NativeSocket = window.WebSocket;
        window.WebSocket = class extends NativeSocket {
          send(data) { if (JSON.parse(data).type !== 'chat.create') super.send(data); }
        };
        """);
    page.navigate(base() + "/chat");
    var create =
        page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("New conversation").setExact(true));
    assertThat(create).isEnabled();
    page.locator("#message").fill("Keep this unsent draft");
    create.click();
    var reconnect =
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reconnect").setExact(true));
    reconnect.waitFor(new Locator.WaitForOptions().setTimeout(20000));
    Path diagnostics = Files.createDirectories(Path.of("build/browser-acceptance/ui-standards"));
    for (int width : List.of(1440, 768, 390)) {
      page.setViewportSize(width, 900);
      if (width <= 1024)
        page.waitForFunction(
            """
          () => { const menu = document.querySelector('.navbar-menu');
            return getComputedStyle(menu).visibility === 'hidden' && menu.getBoundingClientRect().right <= 1; }
          """);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(diagnostics.resolve("conversation-recovery-" + width + ".png"))
              .setFullPage(true));
    }
    reconnect.click();
    assertThat(create).isEnabled();
    assertThat(page.locator("#message")).hasValue("Keep this unsent draft");
  }

  private String base() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path path = Files.createTempDirectory("zalava-ui-standards-");
      Files.writeString(path.resolve("AGENT.md"), "UI standards test workspace.");
      return path;
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ModelConfiguration {
    @Bean
    @Primary
    ChatModel scriptedModel() {
      return new ChatModel() {
        public ChatOptions getOptions() {
          return ToolCallingChatOptions.builder().build();
        }

        public ChatResponse call(Prompt prompt) {
          return new ChatResponse(
              List.of(new Generation(new AssistantMessage("Scripted UI reply"))));
        }

        public Flux<ChatResponse> stream(Prompt prompt) {
          return Flux.just(call(prompt)).delaySubscription(Duration.ofMillis(100));
        }
      };
    }
  }
}
