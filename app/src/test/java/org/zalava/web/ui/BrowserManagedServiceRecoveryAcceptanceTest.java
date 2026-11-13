package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.managedservices.application.ManagedServiceInstallPlanning.AggregatedResources;
import org.zalava.modules.managedservices.application.ManagedServiceInstallPlanning.PlannedService;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;
import org.zalava.modules.managedservices.application.ManagedServiceObservedState;
import org.zalava.modules.managedservices.application.ManagedServiceRecord;
import org.zalava.modules.managedservices.application.port.in.DeclaredManagedServices;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceInstallRequestStore;
import org.zalava.modules.managedservices.application.port.out.ManagedServiceStateStore;
import org.zalava.modules.managedservices.application.port.out.OciServiceEngine;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ModulesControllerComponentTest.ConfiguredRuntimeTestConfiguration.class,
  ModulesControllerComponentTest.ChatModelTestConfiguration.class,
  BrowserManagedServiceRecoveryAcceptanceTest.Fixture.class
})
class BrowserManagedServiceRecoveryAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "recovery-" + UUID.randomUUID();
  private static final String PASSWORD = "RecoveryTestPassword-123";
  @LocalServerPort private int port;
  @Autowired private DeclaredManagedServices declarations;
  @Autowired private ManagedServiceStateStore states;
  @Autowired private ManagedServiceInstallRequestStore requests;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void administratorCanSeeFailureAndRetryAcrossViewports() throws Exception {
    Files.createDirectories(Path.of("build/browser-acceptance"));
    String module = "zalava-module-declared";
    var declaration = declarations.forModule(module).getFirst();
    var desired = declaration.desiredState();
    var grant =
        new ManagedServiceResourceGrant(
            module,
            desired.secretReferences(),
            desired.dataPaths(),
            desired.ports(),
            desired.devices(),
            desired.limits(),
            desired.readinessDeadline(),
            desired.restartLimit());
    var planned =
        new PlannedService(
            module,
            declaration.serviceId(),
            desired,
            grant,
            desired.revision(),
            "grant-1",
            Set.of());
    var aggregate =
        new AggregatedResources(
            desired.ports(),
            desired.secretReferences(),
            desired.dataPaths(),
            desired.devices(),
            desired.limits());
    var request =
        new ManagedServiceInstallRequest(
            UUID.randomUUID().toString(),
            Instant.now(),
            List.of(planned),
            aggregate,
            ManagedServiceInstallRequest.Status.FAILED,
            Instant.now(),
            "failed");
    requests.save(request);
    states.save(
        new ManagedServiceRecord(
            declaration.serviceId(),
            desired,
            desired.revision(),
            grant,
            "grant-1",
            ManagedServiceObservedState.FAILED,
            null,
            "managed-" + declaration.serviceId(),
            1,
            Instant.now().plusSeconds(90),
            "Another container already uses this service name. Resolve the name conflict before retrying."));
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      Page page = browser.newPage();
      page.navigate("http://127.0.0.1:" + port + "/login");
      page.locator("input[name=username]").fill(LOGIN);
      page.locator("input[name=password]").fill(PASSWORD);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      if (page.url().endsWith("/account/password")) {
        page.locator("input[name=currentPassword]").fill(PASSWORD);
        page.locator("input[name=replacementPassword]").fill(PASSWORD + "-changed");
        page.locator("input[name=passwordConfirmation]").fill(PASSWORD + "-changed");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
            .click();
      }
      page.navigate("http://127.0.0.1:" + port + "/modules/" + module);
      assertThat(page.locator("[data-managed-service-endpoint]").count()).isZero();
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, width == 390 ? 844 : width == 768 ? 1024 : 900);
        if (width <= 1024)
          page.waitForFunction(
              "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility==='hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right<=0");
        var retry =
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Retry installation"));
        retry.scrollIntoViewIfNeeded();
        page.getByText(
                "Another container already uses this service name. Resolve the name conflict before retrying.")
            .scrollIntoViewIfNeeded();
        assertThat(retry.isVisible()).isTrue();
        assertThat(page.locator("[data-managed-services]").innerText())
            .contains("Another container already uses this service name");
        assertThat(page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
            .isEqualTo(true);
        assertThat(
                page.locator(".managed-service-image")
                    .evaluate("e => e.scrollWidth <= e.clientWidth"))
            .isEqualTo(true);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/managed-recovery-" + width + ".png")));
      }
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Retry installation"))
          .focus();
      page.keyboard().press("Enter");
      PlaywrightAssertions.assertThat(page.locator("[data-managed-service-state]"))
          .hasText("Running");
      assertThat(requests.find(request.requestId()).orElseThrow().status())
          .isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED);
    }
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-recovery-browser-");
      Files.writeString(root.resolve("AGENT.md"), "Disposable browser test.");
      return root;
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixture {
    @Bean
    ApplicationRunner recoveryAccount(AccountLifecycle accounts) {
      return args ->
          accounts
              .findByLoginName(LOGIN)
              .orElseGet(() -> accounts.create(LOGIN, PASSWORD, AccountRole.ADMIN));
    }

    @Bean
    @Primary
    OciServiceEngine recoveryEngine() {
      return new OciServiceEngine() {
        private boolean exists;
        private boolean running;

        public Observation inspect(String id) {
          return new Observation(
              exists, running, running, "zalava-module-declared", "managed-" + id);
        }

        public void create(ManagedServiceRecord record) {
          exists = true;
        }

        public void start(String id) {
          running = true;
        }

        public void stop(String id) {
          running = false;
        }

        public void remove(String id) {
          exists = false;
        }

        public List<String> recentLogs(String id, int maxLines) {
          return List.of();
        }
      };
    }
  }
}
