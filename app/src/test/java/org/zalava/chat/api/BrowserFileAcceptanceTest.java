package org.zalava.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.FileChooser;
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
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;

/**
 * Real-browser acceptance for bounded attachments. It drives the built React bundle over the real
 * WebSocket transport through the assistant-ui composer primitives and proves task-only
 * attach/use/remove, durable knowledge import, invalid rejection and the mobile composer against
 * real SEA storage and authorization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  ChatControllerComponentTest.ChatModelTestConfiguration.class,
  BrowserFileAcceptanceTest.AccountConfiguration.class
})
class BrowserFileAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final Path DIAGNOSTICS = diagnostics();
  private static final String LOGIN = "browser-file-" + UUID.randomUUID();
  private static final String PASSWORD = "BrowserFilePassword-123";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.chat.attachment.bind-container", () -> "true");
  }

  @Test
  void attachesImportsRemovesAndRejectsBoundedFiles() throws IOException {
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
        signIn(page);
        page.navigate(baseUrl() + "/chat");
        page.getByText("Connected").waitFor();

        attachThroughPicker(page, file("notes.txt", "text/plain", "hello attachment"));
        page.locator(".attachment-chip").getByText("notes.txt").waitFor();

        page.evaluate(
            """
            () => {
              const dropzone = document.querySelector('[data-testid=attachment-dropzone]');
              const transfer = new DataTransfer();
              transfer.items.add(new File(['hover'], 'hover.txt', {type: 'text/plain'}));
              dropzone.dispatchEvent(new DragEvent('dragover', {bubbles: true, dataTransfer: transfer}));
            }
            """);
        page.waitForCondition(
            () ->
                Boolean.TRUE.equals(
                    page.evaluate(
                        "document.querySelector('[data-testid=attachment-dropzone]')"
                            + ".getAttribute('data-dragging') === 'true'")));
        page.evaluate(
            """
            () => {
              const dropzone = document.querySelector('[data-testid=attachment-dropzone]');
              dropzone.dispatchEvent(new DragEvent('dragleave', {bubbles: true}));
            }
            """);
        page.waitForCondition(
            () ->
                Boolean.TRUE.equals(
                    page.evaluate(
                        "document.querySelector('[data-testid=attachment-dropzone]')"
                            + ".getAttribute('data-dragging') === null")));

        page.evaluate(
            """
            () => {
              const dropzone = document.querySelector('[data-testid=attachment-dropzone]');
              const transfer = new DataTransfer();
              transfer.items.add(new File(['dropped body'], 'dropped.txt', {type: 'text/plain'}));
              dropzone.dispatchEvent(new DragEvent('drop', {bubbles: true, dataTransfer: transfer}));
            }
            """);
        page.locator(".attachment-chip").getByText("dropped.txt").waitFor();
        page.locator(".attachment-chip")
            .filter(new Locator.FilterOptions().setHasText("dropped.txt"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Remove"))
            .click();
        page.locator(".attachment-chip")
            .getByText("dropped.txt")
            .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));

        page.locator("#message").fill("attach these");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click();
        page.getByText("Hello from the component test model.").waitFor();

        page.reload();
        page.getByText("Connected").waitFor();
        page.getByText("Attached file: notes.txt").waitFor();
        page.getByText("attach these").waitFor();
        assertThat(page.locator(".attachment-chip").count()).isZero();

        page.locator("#knowledge-import-file")
            .setInputFiles(file("report.txt", "text/plain", "durable report"));
        page.locator(".attachment-list").getByText("Imported").waitFor();

        page.navigate(baseUrl() + "/knowledge");
        page.getByText("report.txt").waitFor();

        page.navigate(baseUrl() + "/chat");
        page.getByText("Connected").waitFor();
        attachThroughPicker(page, file("archive.zip", "application/zip", "not allowed"));
        page.getByRole(AriaRole.ALERT).getByText("Unsupported attachment type").waitFor();

        page.setViewportSize(375, 667);
        assertThat(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Attach files"))
                    .isVisible())
            .isTrue();
        assertThat(page.locator("#knowledge-import-file").isVisible()).isTrue();
        assertThat(page.locator("#message").isVisible()).isTrue();
      } finally {
        context
            .tracing()
            .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("file-trace.zip")));
      }
    }
  }

  private static void attachThroughPicker(Page page, FilePayload payload) {
    FileChooser chooser =
        page.waitForFileChooser(
            () ->
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Attach files"))
                    .click());
    chooser.setFiles(payload);
  }

  private static FilePayload file(String name, String contentType, String body) {
    return new FilePayload(name, contentType, body.getBytes(StandardCharsets.UTF_8));
  }

  private void signIn(Page page) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(LOGIN);
    page.locator("input[name=password]").fill(PASSWORD);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-file-");
      Files.writeString(root.resolve("AGENT.md"), "Browser file acceptance workspace.");
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
    ApplicationRunner browserFileAccount(AccountLifecycle accounts) {
      return arguments -> {
        if (accounts.findByLoginName(LOGIN).isPresent()) {
          return;
        }
        var account = accounts.create(LOGIN, PASSWORD, AccountRole.MEMBER);
        accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
        accounts.changePassword(account.id(), PASSWORD + "-changed", PASSWORD);
      };
    }
  }
}
