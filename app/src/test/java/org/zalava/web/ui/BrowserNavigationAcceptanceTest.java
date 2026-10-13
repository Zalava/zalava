package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.domain.KnowledgeExtractionRecord;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.support.ZalavaComponentTestConfiguration;

/**
 * Real-browser acceptance for the product navigation shell. It proves the same navigation appears
 * on every product page including chat, only links pages the signed-in account can open, and marks
 * the active page.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ZalavaComponentTestConfiguration.class,
  BrowserNavigationAcceptanceTest.AccountConfiguration.class
})
class BrowserNavigationAcceptanceTest {

  private static final Path WORKSPACE = workspace();
  private static final Path DIAGNOSTICS = diagnostics();

  private static final String ADMIN_LOGIN = "browser-nav-admin-" + UUID.randomUUID();
  private static final String ADMIN_PASSWORD = "BrowserNavAdminPassword-123";
  private static final String MEMBER_LOGIN = "browser-nav-member-" + UUID.randomUUID();
  private static final String MEMBER_PASSWORD = "BrowserNavMemberPassword-123";

  private static final List<String> ADMIN_MENU =
      List.of(
          "Dashboard",
          "Chat",
          "Jobs",
          "Knowledge",
          "Memory",
          "Monitoring",
          "Apps",
          "Modules",
          "Settings",
          "Advanced settings",
          "My account");
  private static final List<String> MEMBER_MENU =
      List.of("Dashboard", "Chat", "Jobs", "Knowledge", "Memory", "My account");
  private static final List<String> MEMBER_HIDDEN =
      List.of("/apps", "/modules", "/monitoring", "/settings", "/zalava/control");

  private static final Map<String, String> PAGES = pages();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> ADMIN_LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> ADMIN_PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
  }

  @Test
  void administratorSeesEveryPageNavigationOnEveryPageAndChatMounts() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      startTrace(context);
      try {
        Page page = context.newPage();
        signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
        for (Map.Entry<String, String> pageEntry : PAGES.entrySet()) {
          var response = page.navigate(baseUrl() + pageEntry.getKey());
          assertThat(response.status()).isEqualTo(200);
          page.locator(".zalava-navbar").waitFor();
          assertThat(menuLabels(page)).containsExactlyElementsOf(ADMIN_MENU);
          assertThat(page.locator(".zalava-navbar .navbar-item.is-active").innerText().trim())
              .isEqualTo(pageEntry.getValue());
        }
        for (String link : PAGES.keySet()) {
          assertThat(page.navigate(baseUrl() + link).status()).isLessThan(400);
        }

        page.navigate(baseUrl() + "/chat");
        page.locator(".zalava-navbar").waitFor();
        page.getByText("Connected").waitFor();
        assertThat(page.locator("#root").count()).isEqualTo(1);
      } finally {
        stopTrace(context);
      }
    }
  }

  @Test
  void moduleFreeSettingsDoesNotAdvertiseChannelConfiguration() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      assertThat(page.navigate(baseUrl() + "/settings?section=channels").status()).isEqualTo(200);
      assertThat(
              page.getByText(
                      "No active channel modules are available.",
                      new Page.GetByTextOptions().setExact(false))
                  .count())
          .isEqualTo(1);
      assertThat(page.locator("form[action='/settings/channel-links']").count()).isZero();
      assertThat(page.getByText("Bot token").count()).isZero();
    }
  }

  @Test
  void adaptiveNavigationRetainsAuthorizedDestinationsAcrossRepresentativeWidths()
      throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      for (int[] viewport :
          List.of(new int[] {390, 844}, new int[] {768, 1024}, new int[] {1366, 768})) {
        page.setViewportSize(viewport[0], viewport[1]);
        assertThat(page.navigate(baseUrl() + "/dashboard").status()).isEqualTo(200);
        page.locator(".zalava-navbar").waitFor();
        var brand = page.locator(".zalava-brand").boundingBox();
        var mark = page.locator(".zalava-brand-mark").boundingBox();
        assertThat(mark.x).isGreaterThanOrEqualTo(brand.x);
        assertThat(mark.x + mark.width).isLessThanOrEqualTo(brand.x + brand.width);
        if (viewport[0] <= 1024) {
          page.locator(".navbar-burger").focus();
          page.keyboard().press("Enter");
          page.locator(".zalava-navigation-close")
              .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
          page.keyboard().press("Tab");
          page.waitForFunction(
              "document.activeElement.classList.contains('zalava-navigation-close')");
          assertThat(
                  (Boolean)
                      page.evaluate(
                          "document.activeElement.classList.contains('zalava-navigation-close')"))
              .isTrue();
          var modules =
              page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Modules"));
          modules.waitFor();
          assertThat(modules.isVisible()).isTrue();
          page.keyboard().press("Escape");
          assertThat(
                  (Boolean)
                      page.evaluate("document.activeElement.classList.contains('navbar-burger')"))
              .isTrue();
        } else {
          assertThat(menuLabels(page)).containsExactlyElementsOf(ADMIN_MENU);
        }
        assertThat(
                (Boolean)
                    page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
            .isTrue();
      }
    }
  }

  @Test
  void expandedDashboardPreservesReviewedLayoutAndRetainsScreenshot() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(1366, 768);
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      assertThat(page.navigate(baseUrl() + "/dashboard").status()).isEqualTo(200);
      page.locator(".zalava-navbar").waitFor();
      // PNG byte hashes also encode OS font rendering. Keep the image for visual review
      // and verify the reviewed layout independently of native glyph rasterization.
      page.screenshot(
          new Page.ScreenshotOptions().setPath(DIAGNOSTICS.resolve("dashboard-expanded.png")));
      var sidebar = page.locator(".zalava-navbar").boundingBox();
      var content = page.locator("main").boundingBox();
      assertThat(sidebar.x).isZero();
      assertThat(sidebar.y).isZero();
      assertThat(sidebar.width).isEqualTo(240);
      assertThat(sidebar.height).isEqualTo(768);
      assertThat(content.x).isEqualTo(sidebar.width);
      assertThat(content.width).isEqualTo(1366 - sidebar.width);
      var metrics = page.locator(".dashboard-metric");
      assertThat(metrics.count()).isEqualTo(3);
      var first = metrics.nth(0).boundingBox();
      for (int index = 1; index < metrics.count(); index++) {
        var metric = metrics.nth(index).boundingBox();
        assertThat(metric.y).isEqualTo(first.y);
        assertThat(metric.width).isCloseTo(first.width, Offset.offset(1.0));
        assertThat(metric.x).isGreaterThan(metrics.nth(index - 1).boundingBox().x + metric.width);
      }
      assertThat(page.locator(".dashboard-activity").first().boundingBox().y)
          .isGreaterThan(first.y + first.height);
      assertThat(metrics.first().evaluate("element => getComputedStyle(element).backgroundColor"))
          .isEqualTo("rgb(255, 255, 255)");
      assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
          .isEqualTo(true);
      assertThat(page.locator("a[aria-current='page']").innerText()).isEqualTo("Dashboard");
      page.route("https://**", route -> route.abort());
      page.reload();
      page.locator(".zalava-navbar").waitFor();
      assertThat(page.locator(".zalava-navbar").boundingBox().width).isEqualTo(240);
      assertThat(page.locator("main").boundingBox().x).isEqualTo(240);
    }
  }

  @Test
  void memberOnlySeesAuthorizedNavigation() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      startTrace(context);
      try {
        Page page = context.newPage();
        signIn(page, MEMBER_LOGIN, MEMBER_PASSWORD);
        for (String path : List.of("/dashboard", "/chat", "/jobs", "/knowledge", "/memory")) {
          var response = page.navigate(baseUrl() + path);
          assertThat(response.status()).isEqualTo(200);
          page.locator(".zalava-navbar").waitFor();
          assertThat(menuLabels(page)).containsExactlyElementsOf(MEMBER_MENU);
        }
        page.navigate(baseUrl() + "/chat");
        page.getByText("Ask your workspace administrator to configure a provider.").waitFor();
        assertThat(
                page.getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName("Set up a provider"))
                    .count())
            .isZero();
        for (String hidden : MEMBER_HIDDEN) {
          assertThat(page.navigate(baseUrl() + hidden).status()).isEqualTo(403);
        }
      } finally {
        stopTrace(context);
      }
    }
  }

  @Test
  void changedScreensRemainContainedAndRetainVisualEvidence() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.route("https://**", route -> route.abort());
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        page.navigate(baseUrl() + "/login");
        page.locator(".auth-card").waitFor();
        capture(page, "login-" + width);
        assertContained(page, ".auth-card", ".auth-card input, .auth-card button");
      }
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      page.waitForURL("**/chat");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        for (String path :
            List.of(
                "/chat",
                "/settings",
                "/settings?section=provider",
                "/settings?section=modules",
                "/settings?section=channels",
                "/settings?section=permissions",
                "/zalava/control")) {
          assertThat(page.navigate(baseUrl() + path).status()).isEqualTo(200);
          if (path.equals("/chat")) {
            page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
            assertThat(page.getByText("Model not configured").isVisible()).isTrue();
            assertThat(
                    page.getByRole(
                            AriaRole.LINK, new Page.GetByRoleOptions().setName("Set up a provider"))
                        .isVisible())
                .isTrue();
            assertThat(page.locator(".composer-send").isDisabled()).isTrue();
            assertThat(page.locator("#message").isDisabled()).isTrue();
            assertThat(
                    page.getByRole(
                            AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("New conversation"))
                        .isDisabled())
                .isTrue();
            assertThat(page.locator(".composer-options-toggle").isDisabled()).isTrue();
            var notice = page.locator(".provider-notice").boundingBox();
            assertThat(page.locator(".conversations").boundingBox().y)
                .as("Conversation toolbar follows model setup without unused vertical space")
                .isCloseTo(notice.y + notice.height + 16, Offset.offset(1.0));
            if (width > 1024) {
              assertThat(page.locator(".chat-inspector").boundingBox().height).isGreaterThan(350);
              assertThat(page.locator(".chat-inspector").boundingBox().y)
                  .isEqualTo(page.locator(".chat-primary").boundingBox().y);
            }
          } else if (path.startsWith("/settings")) {
            assertContained(
                page,
                ".settings-card",
                ".settings-card input:not([type=hidden]), .settings-card textarea, .settings-card button, .settings-card .button");
          }
          if (path.equals("/zalava/control") && width <= 640) {
            assertThat(page.locator(".navbar-burger").boundingBox().x).isGreaterThan(width - 80.0);
            page.locator(".navbar-burger").click();
            page.locator(".zalava-navigation-close").waitFor();
            assertThat(page.locator(".zalava-navbar a[aria-current='page']").isVisible()).isTrue();
            page.keyboard().press("Escape");
            page.locator(".navbar-menu")
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
          }
          capture(
              page,
              path.substring(1).replace('/', '-').replace('?', '-').replace('=', '-')
                  + "-"
                  + width);
        }
      }
      page.navigate(baseUrl() + "/settings");
      page.locator("#instructions").fill("Browser saved instructions.");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save instructions"))
          .click();
      page.getByText("Workspace instructions updated.", new Page.GetByTextOptions().setExact(true))
          .waitFor();
      assertThat(Files.readString(WORKSPACE.resolve("AGENT.private.md")))
          .contains("Browser saved instructions.");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset to defaults"))
          .click();
      page.getByText("Default workspace instructions restored.").waitFor();
      assertThat(page.locator("#instructions").inputValue())
          .startsWith("You are Zalava, the assistant for this workspace.");
      assertThat(Files.readString(WORKSPACE.resolve("AGENT.private.md")))
          .contains("You are Zalava, the assistant for this workspace.");
      capture(page, "settings-defaults-1440");
    }
  }

  @Autowired AccountLifecycle accounts;
  @Autowired KnowledgeSourceLifecycle knowledgeLifecycle;
  @Autowired KnowledgeExtractionRecordStore extractionRecords;

  @Test
  void temporaryAdministratorMustChangePasswordBeforeChatAndOldPasswordStopsWorking()
      throws Exception {
    String login = "browser-temporary-" + UUID.randomUUID();
    String temporary = "TemporaryBrowserPassword-123";
    String replacement = "ReplacementBrowserPassword-456";
    var account = accounts.create(login, temporary, AccountRole.ADMIN);
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(1440, 1000);
      signIn(page, login, temporary);
      page.waitForURL("**/account/password");
      page.navigate(baseUrl() + "/settings");
      page.waitForURL("**/account/password");
      assertThat(page.locator(".auth-card").isVisible()).isTrue();
      assertThat(
              page.locator(".auth-card")
                  .evaluate("element => getComputedStyle(element).backgroundColor"))
          .isEqualTo("rgb(255, 255, 255)");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        assertContained(
            page, ".auth-card", ".auth-card input:not([type=hidden]), .auth-card button");
        capture(page, "password-required-" + width);
      }
      page.locator("input[name=currentPassword]").fill("WrongCurrentPassword-123");
      page.locator("input[name=replacementPassword]").fill(replacement);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
          .click();
      page.getByRole(AriaRole.ALERT).waitFor();
      assertThat(accounts.findByLoginName(login).orElseThrow().passwordChangeRequired()).isTrue();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        capture(page, "password-error-" + width);
      }
      page.locator("input[name=currentPassword]").fill(temporary);
      page.locator("input[name=replacementPassword]").fill(replacement);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
          .click();
      page.waitForURL("**/chat");
      assertThat(accounts.findByLoginName(login).orElseThrow().passwordChangeRequired()).isFalse();
    }
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(1440, 1000);
      signIn(page, login, temporary);
      page.waitForURL("**/login?error");
      assertThat(page.getByRole(AriaRole.ALERT).isVisible()).isTrue();
      capture(page, "login-error-1440");
      signIn(page, login, replacement);
      page.waitForURL("**/chat");
    }
  }

  @Test
  void accountsAndCompactNavigationRemainReadableAndContained() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      page.waitForURL("**/chat");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        page.navigate(baseUrl() + "/zalava/accounts");
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(DIAGNOSTICS.resolve("accounts-" + width + ".png"))
                .setFullPage(true));
        assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
            .isEqualTo(true);
        assertThat(page.locator(".zalava-navbar").count()).isEqualTo(1);
        for (Locator control :
            page.locator("main input:not([type=hidden]), main select, main button").all()) {
          var bounds = control.boundingBox();
          if (bounds == null) continue;
          assertThat(bounds.x).isGreaterThanOrEqualTo(0);
          assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(width + 1.0);
        }
        if (width <= 1024) {
          page.locator(".navbar-burger").click();
          page.locator(".zalava-navigation-close").waitFor();
          page.waitForFunction(
              "document.querySelector('.navbar-menu').getBoundingClientRect().left >= -0.5");
          assertThat(page.locator(".navbar-menu").boundingBox().height)
              .isGreaterThanOrEqualTo(900.0);
          for (Locator label : page.locator(".zalava-nav-label").all()) {
            assertThat(label.boundingBox().width)
                .as("Full destination names remain visibly readable")
                .isGreaterThan(20);
          }
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(DIAGNOSTICS.resolve("navigation-readable-" + width + ".png")));
          page.keyboard().press("Escape");
        }
      }
    }
  }

  @Test
  void tabletNavigationDisplaysFullDestinationNames() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(768, 900);
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      page.waitForURL("**/chat");
      Locator burger = page.locator(".navbar-burger");
      if (burger.isVisible()) burger.click();
      page.waitForFunction(
          "document.querySelector('.navbar-menu').getBoundingClientRect().left >= -0.5");
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(DIAGNOSTICS.resolve("navigation-readable-tablet.png")));
      assertThat(page.locator(".navbar-menu").boundingBox().height).isGreaterThanOrEqualTo(900.0);
      for (Locator label : page.locator(".zalava-nav-label").all()) {
        assertThat(label.boundingBox().width)
            .as("Visible destination labels on tablet")
            .isGreaterThan(20);
      }
    }
  }

  @Test
  void accountActionsConfirmPersistenceAndPreserveSafeDuplicateInput() throws Exception {
    String login = "browser-managed-" + UUID.randomUUID();
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.setViewportSize(1440, 900);
      signIn(page, ADMIN_LOGIN, ADMIN_PASSWORD);
      page.waitForURL("**/chat");
      page.navigate(baseUrl() + "/zalava/accounts");
      page.locator("#create-login").fill(login);
      page.locator("#create-password").fill("ManagedBrowserPassword-123");
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account").setExact(true))
          .click();
      page.getByRole(AriaRole.STATUS)
          .getByText("Account created", new Locator.GetByTextOptions().setExact(false))
          .waitFor();
      var managed = accounts.findByLoginName(login).orElseThrow();
      String card = "[data-account-id='" + managed.id().value() + "']";
      page.locator(card)
          .getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Disable").setExact(true))
          .click();
      page.getByRole(AriaRole.STATUS)
          .getByText("Account disabled", new Locator.GetByTextOptions().setExact(false))
          .waitFor();
      assertThat(accounts.findById(managed.id()).orElseThrow().enabled()).isFalse();
      page.locator(card)
          .getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Enable").setExact(true))
          .click();
      page.getByRole(AriaRole.STATUS)
          .getByText("Account enabled.", new Locator.GetByTextOptions().setExact(true))
          .waitFor();
      assertThat(accounts.findById(managed.id()).orElseThrow().enabled()).isTrue();
      page.locator("#create-login").fill(login);
      page.locator("#create-password").fill("ManagedBrowserPassword-123");
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create account").setExact(true))
          .click();
      page.getByRole(AriaRole.ALERT).getByText("Login name is already in use").waitFor();
      assertThat(page.locator("#create-login").inputValue()).isEqualTo(login);
      assertThat(page.locator("#create-password").inputValue()).isEmpty();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        capture(page, "accounts-duplicate-feedback-" + width);
      }
    }
  }

  @Test
  void memberPersonalPageAndDeniedPagesRetainSafeRecoveryAtAllWidths() throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, MEMBER_LOGIN, MEMBER_PASSWORD);
      page.waitForURL("**/chat");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        assertThat(page.navigate(baseUrl() + "/account/password").status()).isEqualTo(200);
        assertThat(page.locator(".auth-card").innerText())
            .contains(MEMBER_LOGIN)
            .doesNotContain("temporary or has been reset");
        assertThat(
                page.locator("body")
                    .evaluate("element => getComputedStyle(element).fontFamily")
                    .toString())
            .doesNotContain("Times");
        assertContained(
            page,
            ".auth-card",
            ".auth-card input:not([type=hidden]), .auth-card button, .auth-card a");
        capture(page, "personal-account-" + width);
        for (String path : List.of("/settings", "/knowledge/not-a-source")) {
          int expected = path.equals("/settings") ? 403 : 404;
          assertThat(page.navigate(baseUrl() + path).status()).isEqualTo(expected);
          assertThat(page.locator("body").innerText()).doesNotContain("Whitelabel", "not-a-source");
          assertThat(
                  page.getByRole(
                          AriaRole.LINK, new Page.GetByRoleOptions().setName("Return to Chat"))
                      .isVisible())
              .isTrue();
          capture(page, "error-" + expected + "-" + width);
        }
      }
    }
  }

  @Test
  void knowledgeSearchControlsStayWithinViewport() throws Exception {
    checkLibrarySearch("/knowledge");
  }

  @Test
  void memorySearchControlsStayWithinViewport() throws Exception {
    checkLibrarySearch("/memory");
  }

  private void checkLibrarySearch(String path) throws Exception {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, MEMBER_LOGIN, MEMBER_PASSWORD);
      page.waitForURL("**/chat");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        assertThat(page.navigate(baseUrl() + path).status()).isEqualTo(200);
        capture(page, path.substring(1) + "-search-" + width);
        for (Locator control :
            page.locator(
                    "form[action='"
                        + path
                        + "'] input, form[action='"
                        + path
                        + "'] select, form[action='"
                        + path
                        + "'] button")
                .all()) {
          var bounds = control.boundingBox();
          assertThat(bounds.x).isGreaterThanOrEqualTo(0);
          assertThat(bounds.x + bounds.width)
              .as("Search controls are visible within the viewport")
              .isLessThanOrEqualTo(width + 1.0);
        }
      }
    }
  }

  @Test
  void knowledgeUploadSharingAndMemberReadOnlyViewsWorkThroughTheUi() throws Exception {
    String name = "browser-library-" + UUID.randomUUID() + ".txt";
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext adminContext = browser.newContext();
        BrowserContext memberContext = browser.newContext()) {
      Page admin = adminContext.newPage();
      signIn(admin, ADMIN_LOGIN, ADMIN_PASSWORD);
      admin.waitForURL("**/chat");
      admin.navigate(baseUrl() + "/knowledge");
      admin
          .locator("#knowledge-file")
          .setInputFiles(
              new FilePayload(
                  name,
                  "text/plain",
                  "Synthetic library acceptance document.".getBytes(StandardCharsets.UTF_8)));
      admin
          .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload").setExact(true))
          .click();
      admin.getByRole(AriaRole.STATUS).getByText("Source uploaded for processing.").waitFor();
      Locator sourceLink =
          admin.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
      String href = sourceLink.getAttribute("href");
      Locator card = admin.locator("li.box").filter(new Locator.FilterOptions().setHas(sourceLink));
      for (int width : List.of(390, 768, 1440)) {
        admin.setViewportSize(width, 900);
        capture(admin, "knowledge-pending-" + width);
        assertThat(admin.navigate(baseUrl() + href).status()).isEqualTo(200);
        assertThat(admin.locator("body").innerText())
            .contains("Waiting for a document processing module");
        capture(admin, "knowledge-processing-detail-" + width);
        admin.navigate(baseUrl() + "/knowledge");
      }
      // Seed only a processor outcome for visual diagnostics; this does not claim real extraction.
      var owner = new Actor(accounts.findByLoginName(ADMIN_LOGIN).orElseThrow().id());
      var sourceId = new KnowledgeSourceId(UUID.fromString(href.substring("/knowledge/".length())));
      var candidate = knowledgeLifecycle.beginReprocessing(owner, sourceId, "qa-parser", "1");
      extractionRecords.record(
          KnowledgeExtractionRecord.failed(
              candidate,
              ContentExtractionFailureCategory.MALFORMED_INPUT,
              "private processor fixture detail"));
      knowledgeLifecycle.completeReprocessing(owner, candidate, false);
      for (int width : List.of(390, 768, 1440)) {
        admin.setViewportSize(width, 900);
        admin.navigate(baseUrl() + href);
        assertThat(admin.locator("body").innerText())
            .contains("Processing failed", "The document could not be read")
            .doesNotContain("private processor fixture detail");
        capture(admin, "knowledge-failed-processing-" + width);
      }
      admin.navigate(baseUrl() + "/knowledge");
      card.getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Share").setExact(true))
          .click();
      admin.getByRole(AriaRole.STATUS).getByText("Source sharing updated.").waitFor();
      Page member = memberContext.newPage();
      signIn(member, MEMBER_LOGIN, MEMBER_PASSWORD);
      member.waitForURL("**/chat");
      for (int width : List.of(390, 768, 1440)) {
        member.setViewportSize(width, 900);
        member.navigate(baseUrl() + "/knowledge");
        Locator sharedLink =
            member.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
        Locator sharedCard =
            member.locator("li.box").filter(new Locator.FilterOptions().setHas(sharedLink));
        assertThat(sharedCard.innerText()).contains("Shared read-only");
        assertThat(sharedCard.locator("form").count()).isZero();
        capture(member, "knowledge-shared-read-only-" + width);
      }
      card.getByRole(
              AriaRole.BUTTON,
              new Locator.GetByRoleOptions().setName("Make private").setExact(true))
          .click();
      admin.getByRole(AriaRole.STATUS).getByText("Source sharing updated.").waitFor();
      assertThat(member.navigate(baseUrl() + href).status()).isEqualTo(404);
      admin.reload();
      assertThat(
              card.getByRole(
                      AriaRole.BUTTON,
                      new Locator.GetByRoleOptions().setName("Share").setExact(true))
                  .count())
          .isEqualTo(1);
      card.getByRole(
              AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete").setExact(true))
          .click();
      admin.getByRole(AriaRole.STATUS).getByText("Source deleted.").waitFor();
      admin.reload();
      assertThat(
              admin
                  .getByRole(
                      AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true))
                  .count())
          .isZero();
      admin
          .locator("#knowledge-file")
          .setInputFiles(
              new FilePayload(
                  "invalid.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)));
      admin
          .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload").setExact(true))
          .click();
      admin.getByRole(AriaRole.ALERT).getByText("Source upload could not be completed.").waitFor();
      for (int width : List.of(390, 768, 1440)) {
        admin.setViewportSize(width, 900);
        capture(admin, "knowledge-upload-error-" + width);
      }
    }
  }

  @Test
  void memoryScopeExplanationReviewAndDetailsRemainReadable() throws Exception {
    String text = "Browser private preference " + UUID.randomUUID();
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page, MEMBER_LOGIN, MEMBER_PASSWORD);
      page.waitForURL("**/chat");
      page.navigate(baseUrl() + "/memory");
      page.locator("#proposal-scope").selectOption("USER");
      page.locator("#proposal-text").fill(text);
      page.locator("[data-action=propose]").click();
      Locator proposal =
          page.locator("[data-memory-proposal]")
              .filter(new Locator.FilterOptions().setHasText(text));
      proposal.waitFor();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        capture(page, "memory-proposal-" + width);
      }
      proposal.locator("[data-action=approve]").click();
      page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true))
          .click();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 900);
        assertThat(page.locator("body").innerText())
            .contains(
                "Private to your account", "Personal preferences", "Approved memory proposal");
        capture(page, "memory-readable-detail-" + width);
      }
      page.getByText("Stored metadata", new Page.GetByTextOptions().setExact(true)).click();
      assertThat(page.locator("[data-memory-id]").isVisible()).isTrue();
      capture(page, "memory-metadata-expanded-1440");
    }
  }

  private void capture(Page page, String name) {
    page.waitForFunction(
        """
        () => {
          const menu = document.querySelector('.navbar-menu');
          if (!menu || window.innerWidth > 1024) return true;
          const bounds = menu.getBoundingClientRect();
          return menu.classList.contains('is-active') ? Math.abs(bounds.left) < 0.5 : bounds.right <= 0.5;
        }
        """);
    assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
        .isEqualTo(true);
    page.screenshot(
        new Page.ScreenshotOptions().setPath(DIAGNOSTICS.resolve(name + ".png")).setFullPage(true));
  }

  private void assertContained(Page page, String container, String controls) {
    var box = page.locator(container).boundingBox();
    for (var control : page.locator(controls).all()) {
      var bounds = control.boundingBox();
      if (bounds == null) continue;
      assertThat(bounds.x).isGreaterThanOrEqualTo(box.x);
      assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(box.x + box.width + 1);
    }
  }

  private List<String> menuLabels(Page page) {
    return page.locator(".zalava-navbar .navbar-start a").allTextContents();
  }

  private void startTrace(BrowserContext context) {
    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
  }

  private void stopTrace(BrowserContext context) throws IOException {
    Files.createDirectories(DIAGNOSTICS);
    context
        .tracing()
        .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("navigation-trace.zip")));
  }

  private void signIn(Page page, String login, String password) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(login);
    page.locator("input[name=password]").fill(password);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Map<String, String> pages() {
    Map<String, String> pages = new LinkedHashMap<>();
    pages.put("/dashboard", "Dashboard");
    pages.put("/chat", "Chat");
    pages.put("/jobs", "Jobs");
    pages.put("/knowledge", "Knowledge");
    pages.put("/memory", "Memory");
    pages.put("/monitoring", "Monitoring");
    pages.put("/apps", "Apps");
    pages.put("/modules", "Modules");
    pages.put("/settings", "Settings");
    pages.put("/zalava/control", "Advanced settings");
    return pages;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-navigation-");
      Files.writeString(root.resolve("AGENT.md"), "Browser navigation acceptance workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static Path diagnostics() {
    try {
      return Files.createDirectories(Path.of("build", "browser-acceptance"));
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserNavigationAccounts(AccountLifecycle accounts) {
      return arguments -> {
        ensureActivated(accounts, ADMIN_LOGIN, ADMIN_PASSWORD, AccountRole.ADMIN);
        ensureActivated(accounts, MEMBER_LOGIN, MEMBER_PASSWORD, AccountRole.MEMBER);
      };
    }

    private static void ensureActivated(
        AccountLifecycle accounts, String login, String password, AccountRole role) {
      var account =
          accounts.findByLoginName(login).orElseGet(() -> accounts.create(login, password, role));
      if (account.passwordChangeRequired()) {
        accounts.changePassword(account.id(), password, password + "-changed");
        accounts.changePassword(account.id(), password + "-changed", password);
      }
    }
  }
}
