package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ModulesControllerComponentTest.ConfiguredRuntimeTestConfiguration.class,
  BrowserModuleConfigurationAcceptanceTest.AccountConfiguration.class
})
class BrowserModuleConfigurationAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "module-browser-" + UUID.randomUUID();

  @LocalServerPort private int port;

  @org.springframework.beans.factory.annotation.Autowired
  private FileSystemModuleConfigurationStore configurations;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add(
        "spring.allConfig.location",
        () -> WORKSPACE.resolve("private/application.private.yaml").toString());
    registry.add(
        "zalava.module-configuration.root",
        () -> WORKSPACE.resolve("module-configuration").toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> "ModuleBrowserPassword-123");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @BeforeEach
  void existingConfiguration() {
    configurations.saveCandidate(
        new ModuleConfigurationSnapshot(
            "configured-search",
            "1.0.0",
            "schema-1",
            Map.of(
                "search",
                Map.of("endpoint", "https://old.example.test", "credentialRef", "search-key")),
            Map.of("search.credentialRef", "search-key")),
        Map.of("search-key", "old-secret"));
    configurations.promoteCandidate("configured-search");
  }

  @Test
  void stagesScopedModuleConfigurationWithoutRenderingTheStoredSecret() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill("ModuleBrowserPassword-123");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Sign in"))
          .click();
      page.locator("input[name=currentPassword]").fill("ModuleBrowserPassword-123");
      page.locator("input[name=replacementPassword]").fill("ModuleBrowserPassword-456");
      page.getByRole(
              com.microsoft.playwright.options.AriaRole.BUTTON,
              new Page.GetByRoleOptions().setName("Change password"))
          .click();
      page.navigate(baseUrl() + "/modules/configured-search");
      assertThat(page.url()).endsWith("/modules/configured-search");
      assertThat(page.locator("body").innerText()).doesNotContain("old-secret");
      page.locator("input[name='configuration.search.endpoint']").fill("https://new.example.test");
      page.locator("input[name='replacement-secret.search.credentialRef']").fill("new-secret");
      page.locator("form[action='/modules/configured-search/configuration'] button[type=submit]")
          .first()
          .click();
      page.getByText("Configuration saved.").waitFor();
    }
    assertThat(configurations.candidate("configured-search")).isPresent();
    assertThat(
            configurations
                .candidateSecrets("configured-search")
                .resolve("search-key")
                .map(String::new))
        .hasValue("new-secret");
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-modules-");
      Files.writeString(root.resolve("AGENT.md"), "Browser module configuration workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserModuleAccount(AccountLifecycle accounts) {
      return arguments ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(
                  () -> accounts.create(LOGIN, "ModuleBrowserPassword-123", AccountRole.ADMIN));
    }
  }
}
