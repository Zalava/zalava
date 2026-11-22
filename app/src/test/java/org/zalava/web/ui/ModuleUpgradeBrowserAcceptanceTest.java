package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
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
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.ZalavaRuntime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ModuleLifecycleComponentTest.FixtureConfiguration.class,
  ModuleUpgradeBrowserAcceptanceTest.AccountConfiguration.class
})
class ModuleUpgradeBrowserAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "upgrade-browser-" + UUID.randomUUID();
  @LocalServerPort int port;
  @Autowired FileSystemModuleConfigurationStore configurations;
  @Autowired ZalavaRuntime runtime;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("zalava.module-configuration.root", WORKSPACE::toString);
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void administratorCanCorrectIncompatibleSavedSettings() throws Exception {
    configurations.saveCandidate(
        new ModuleConfigurationSnapshot(
            "lifecycle-fixture",
            "0.9.0",
            "old",
            Map.of("fixture", Map.of("endpoint", 123)),
            Map.of()),
        Map.of());
    configurations.promoteCandidate("lifecycle-fixture");
    assertThatThrownBy(() -> ((ManagedZalavaRuntime) runtime).start("lifecycle-fixture"))
        .isInstanceOf(IllegalArgumentException.class);
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright
                .chromium()
                .launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      String base = "http://127.0.0.1:" + port;
      page.navigate(base + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("UpgradeBrowserPassword-123");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      page.locator("input[name=currentPassword]").fill("UpgradeBrowserPassword-123");
      page.locator("input[name=replacementPassword]").fill("UpgradeBrowserPassword-456");
      page.locator("input[name=passwordConfirmation]").fill("UpgradeBrowserPassword-456");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
          .click();
      page.navigate(base + "/modules/lifecycle-fixture");
      Path screenshots = Path.of("/tmp/zalava-upgrade-recovery-screenshots");
      Files.createDirectories(screenshots);
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        page.navigate(base + "/modules/lifecycle-fixture");
        assertThat(
                page.getByText(
                        "Review the saved settings for this module version.",
                        new Page.GetByTextOptions().setExact(false))
                    .isVisible())
            .isTrue();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(screenshots.resolve(width + ".png"))
                .setFullPage(true));
      }
      page.locator("input[name='configuration.fixture.endpoint']").fill("ready");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save configuration"))
          .click();
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start module")).click();
      assertThat(page.locator("[data-runtime-state]").getAttribute("data-runtime-state"))
          .isEqualTo("RUNNING");
    }
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-upgrade-browser-");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(
                  () -> accounts.create(LOGIN, "UpgradeBrowserPassword-123", AccountRole.ADMIN));
    }
  }
}
