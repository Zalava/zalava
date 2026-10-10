package org.zalava.assistant.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;

/** Exercises the secured first-run path with real form sessions and persisted workspace state. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ChatControllerComponentTest.ChatModelTestConfiguration.class,
  BrowserOnboardingAcceptanceTest.AccountConfiguration.class
})
class BrowserOnboardingAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "onboarding-browser-" + UUID.randomUUID();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
    registry.add("agent.onboarding.completed", () -> "false");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> "OnboardingTestPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void replacesBootstrapPasswordConfiguresProviderInSettingsAndSignsOut() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("wrong-password");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      assertThat(page.url()).contains("/login?error");

      page.navigate(baseUrl() + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("OnboardingTestPassword-123");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      assertThat(page.url()).endsWith("/account/password");
      page.locator("input[name=currentPassword]").fill("OnboardingTestPassword-123");
      page.locator("input[name=replacementPassword]").fill("OnboardingTestPassword-456");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
          .click();

      page.waitForURL("**/chat");
      assertThat(
              page.getByRole(
                      AriaRole.LINK, new Page.GetByRoleOptions().setName("Set up a provider"))
                  .getAttribute("href"))
          .isEqualTo("/settings?section=provider");
      page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Set up a provider"))
          .click();
      assertThat(page.locator(".settings-sections").isVisible()).isTrue();
      page.locator("#provider-choice").selectOption("anthropic");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Choose provider"))
          .click();
      page.locator("input[name=apiKey]").fill("browser-provider-key");
      page.locator("input[name=model]").fill("test-model");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save provider")).click();
      page.getByText("Provider configuration saved.", new Page.GetByTextOptions().setExact(false))
          .waitFor();
      assertThat(page.locator("input[name=apiKey]").inputValue()).isEmpty();
      page.reload();
      assertThat(page.locator("input[name=model]").inputValue()).isEqualTo("test-model");
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        page.navigate(baseUrl() + "/settings?section=provider");
        page.locator(".settings-card").waitFor();
        assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
            .isTrue();
        for (Locator control :
            page.locator(
                    ".settings-card input:not([type=hidden]), .settings-card select, .settings-card button")
                .all()) {
          var box = control.boundingBox();
          assertThat(box.x).isGreaterThanOrEqualTo(0);
          assertThat(box.x + box.width).isLessThanOrEqualTo(width + 1.0);
        }
        Path screenshot = Path.of("build/browser-acceptance/provider-settings-" + width + ".png");
        Files.createDirectories(screenshot.getParent());
        settle(page);
        page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
      }
      for (String provider : List.of("google-vertex", "bedrock-converse")) {
        for (int width : List.of(390, 768, 1440)) {
          page.setViewportSize(width, 1000);
          page.navigate(baseUrl() + "/settings?section=provider&provider=" + provider);
          page.locator(".settings-card").waitFor();
          assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
              .isTrue();
          settle(page);
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(
                      Path.of(
                          "build/browser-acceptance/provider-" + provider + "-" + width + ".png"))
                  .setFullPage(true));
        }
      }
      page.navigate(baseUrl() + "/settings?section=provider");
      page.locator("input[name=baseUrl]").fill("not-an-endpoint");
      page.evaluate(
          """
          document.querySelector("form[action='/settings/provider']")
            .addEventListener("submit", event => event.preventDefault(), {once:true});
          """);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save provider")).click();
      assertThat(page.locator("button[data-pending=true]").isDisabled()).isTrue();
      settle(page);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/provider-pending.png"))
              .setFullPage(true));
      page.evaluate(
          """
          document.querySelector("form[action='/settings/provider']").submit();
          """);
      page.getByRole(AriaRole.ALERT).waitFor();
      for (int width : List.of(390, 768, 1440)) {
        page.setViewportSize(width, 1000);
        settle(page);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/provider-error-" + width + ".png"))
                .setFullPage(true));
      }
      page.setViewportSize(390, 1000);
      page.locator(".navbar-burger").click();
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out").setExact(true))
          .waitFor();
      settle(page);
      assertThat(page.locator(".navbar-menu").boundingBox().height).isGreaterThanOrEqualTo(999);
      var signOutBox =
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out").setExact(true))
              .boundingBox();
      assertThat(signOutBox.y + signOutBox.height).isLessThanOrEqualTo(1000);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/provider-mobile-signout.png"))
              .setFullPage(true));
      page.getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out").setExact(true))
          .click();
      page.waitForURL("**/login?logout");
      page.navigate(baseUrl() + "/chat");
      assertThat(page.url()).contains("/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("OnboardingTestPassword-123");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true))
          .click();
      assertThat(page.url()).contains("/login?error");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("OnboardingTestPassword-456");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true))
          .click();
      page.waitForURL("**/chat");
    }
    assertThat(new ModelProviderStore(WORKSPACE).path())
        .content()
        .contains("anthropic")
        .contains("browser-provider-key");
    assertThat(WORKSPACE.resolve("AGENT.private.md")).doesNotExist();
  }

  private static void settle(Page page) {
    page.evaluate(
        "new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))");
    page.waitForFunction(
        "document.getAnimations().filter(a => a instanceof CSSTransition).every(a => a.playState === 'finished')");
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-onboarding-");
      Files.writeString(root.resolve("AGENT.md"), "Browser onboarding test workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner onboardingBrowserAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(
                  () -> accounts.create(LOGIN, "OnboardingTestPassword-123", AccountRole.ADMIN));
    }
  }
}
