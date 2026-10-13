package org.zalava.assistant.chat.api;

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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.support.DocumentFixtureRuntime;

/**
 * Real-browser acceptance for bounded attachments. It drives the built React bundle over the real
 * WebSocket transport through the assistant-ui composer primitives and proves task-only
 * attach/use/remove, durable knowledge import, invalid rejection and the mobile composer against
 * real Zalava storage and authorization.
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

  @TestBean(name = "zalavaRuntime", methodName = "artifactRuntime", enforceOverride = true)
  ZalavaRuntime runtime;

  static ZalavaRuntime artifactRuntime() throws Exception {
    return new DocumentFixtureRuntime();
  }

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> LOGIN);
    registry.add("zalava.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "openai");
    registry.add("spring.ai.openai.api-key", () -> "local-scripted-model");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("zalava.chat.attachment.bind-container", () -> "true");
  }

  @Test
  void attachesImportsRemovesAndRejectsBoundedFiles() throws IOException {
    ((DocumentFixtureRuntime) runtime).available = true;
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
        page.locator(".message.user .attachment-chip").getByText("notes.txt").waitFor();
        assertThat(page.locator(".message.user").innerText())
            .contains("attach these", "notes.txt")
            .doesNotContain("sha256", "bytes");
        page.locator(".message.user").getByText("attach these").waitFor();
        assertThat(
                page.locator(
                        "[data-testid=composer] #message, [data-testid=composer] .composer-send, [data-testid=composer] .attachment-name")
                    .evaluateAll(
                        "nodes => nodes.every(n => { const b=n.getBoundingClientRect(); const c=n.closest(\"[data-testid=composer]\").getBoundingClientRect(); return b.left >= c.left && b.right <= c.right; })"))
            .isEqualTo(true);
        assertThat(page.locator("[data-testid=composer] .attachment-chip").count()).isZero();
        page.locator(".uploaded-file-options summary").click();
        page.locator(".uploaded-file-options li")
            .filter(new Locator.FilterOptions().setHasText("notes.txt"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save to knowledge"))
            .click();
        page.locator(".uploaded-file-options li")
            .filter(new Locator.FilterOptions().setHasText("notes.txt"))
            .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Open in Knowledge"))
            .waitFor();
        captureFileState(page, "chat-existing-upload-saved");
        page.reload();
        page.getByText("Connected").waitFor();
        page.locator(".uploaded-file-options summary").click();
        assertThat(
                page.locator(".uploaded-file-options li")
                    .filter(new Locator.FilterOptions().setHasText("notes.txt"))
                    .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Save to knowledge"))
                    .count())
            .isZero();
        page.locator(".uploaded-file-options li")
            .filter(new Locator.FilterOptions().setHasText("notes.txt"))
            .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Open in Knowledge"))
            .click();
        assertThat(page.locator("main").innerText()).contains("notes.txt");
        page.navigate(baseUrl() + "/chat");
        page.getByText("Connected").waitFor();

        attachThroughPicker(
            page,
            file("long-document-name-".repeat(7) + "report.txt", "text/plain", "durable report"));
        page.locator("[data-testid=composer] .attachment-chip").getByText("report.txt").waitFor();
        captureFileState(page, "chat-staged-knowledge-save");
        page.locator("[data-testid=composer] .attachment-chip")
            .filter(new Locator.FilterOptions().setHasText("report.txt"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save to knowledge"))
            .click();
        page.locator(".attachment-list")
            .getByText("Saved to Knowledge:", new Locator.GetByTextOptions().setExact(false))
            .waitFor();

        page.navigate(baseUrl() + "/knowledge");
        page.getByText("report.txt").waitFor();

        page.navigate(baseUrl() + "/chat");
        page.getByText("Connected").waitFor();
        attachThroughPicker(page, file("archive.zip", "application/zip", "not allowed"));
        page.getByRole(AriaRole.ALERT).getByText("Unsupported attachment type").waitFor();

        page.setViewportSize(375, 667);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options"))
            .click();
        assertThat(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Attach files"))
                    .isVisible())
            .isTrue();
        assertThat(
                page.getByRole(
                        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Import to knowledge"))
                    .count())
            .isZero();
        assertThat(page.locator("#message").isVisible()).isTrue();
        for (int width : List.of(390, 768, 1440)) {
          page.setViewportSize(width, 1000);
          page.evaluate(
              "new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))");
          page.waitForFunction(
              "document.getAnimations().filter(a => a instanceof CSSTransition).every(a => a.playState === 'finished')");
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(DIAGNOSTICS.resolve("chat-file-menu-" + width + ".png"))
                  .setFullPage(true));
          page.keyboard().press("Escape");
          assertThat(
                  page.getByRole(
                          AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options"))
                      .getAttribute("aria-expanded"))
              .isEqualTo("false");
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(DIAGNOSTICS.resolve("chat-composer-" + width + ".png"))
                  .setFullPage(true));
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options"))
              .click();
        }
      } finally {
        context
            .tracing()
            .stop(new Tracing.StopOptions().setPath(DIAGNOSTICS.resolve("file-trace.zip")));
      }
    }
  }

  @Test
  void missingDocumentProcessorExplainsBothFileOptionsAndSendFailure() throws Exception {
    ((DocumentFixtureRuntime) runtime).available = false;
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);
      page.navigate(baseUrl() + "/chat");
      page.getByText("Connected").waitFor();
      for (int width : new int[] {390, 768, 1440}) {
        page.setViewportSize(width, 900);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options"))
            .click();
        page.locator(".document-processing-notice")
            .getByText("A document-processing module is needed to read files.")
            .waitFor();
        assertThat(page.locator(".document-processing-notice").innerText())
            .contains("Reading attachments", "Knowledge imports", "active module");
        page.waitForFunction(
            "() => { const m=document.querySelector('.navbar-menu'); return window.innerWidth > 1024 || !m || m.getBoundingClientRect().right <= .5; }");
        page.locator(".document-processing-notice").scrollIntoViewIfNeeded();
        assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
            .isEqualTo(true);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(DIAGNOSTICS.resolve("chat-document-prerequisite-" + width + ".png")));
        page.keyboard().press("Escape");
      }
      attachThroughPicker(page, file("unread.txt", "text/plain", "No reading without a module"));
      page.locator(".attachment-chip").getByText("unread.txt").waitFor();
      page.locator("[data-testid=composer] .attachment-chip")
          .filter(new Locator.FilterOptions().setHasText("unread.txt"))
          .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save to knowledge"))
          .click();
      page.locator(".attachment-list")
          .getByText(
              "A document-processing module is needed to read it",
              new Locator.GetByTextOptions().setExact(false))
          .waitFor();

      page.locator("#message").fill("Read this attached file");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      page.getByRole(AriaRole.ALERT)
          .getByText(
              "Reading attached files needs an active document-processing module",
              new Locator.GetByTextOptions().setExact(false))
          .waitFor();
    } finally {
      ((DocumentFixtureRuntime) runtime).available = true;
    }
  }

  private static void captureFileState(Page page, String prefix) {
    for (int width : new int[] {390, 768, 1440}) {
      page.setViewportSize(width, 900);
      page.waitForFunction(
          "() => { const m=document.querySelector('.navbar-menu'); return window.innerWidth > 1024 || !m || m.getBoundingClientRect().right <= .5; }");
      page.locator("[data-testid=composer]").scrollIntoViewIfNeeded();
      assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"))
          .isEqualTo(true);
      assertThat(
              page.locator("[data-testid=composer] .attachment-chip button")
                  .evaluateAll(
                      "nodes => nodes.every(n => { const b=n.getBoundingClientRect(); return b.left >= 0 && b.right <= window.innerWidth; })"))
          .isEqualTo(true);
      assertThat(
              page.locator("[data-testid=composer] .attachment-chip")
                  .evaluateAll(
                      "nodes => nodes.every(n => n.getBoundingClientRect().height <= 140)"))
          .isEqualTo(true);
      Locator controls =
          page.locator(
              "[data-testid=composer] #message, [data-testid=composer] .composer-send, [data-testid=composer] .attachment-name");
      assertThat(controls.count()).isGreaterThanOrEqualTo(2);
      assertThat(
              controls.evaluateAll(
                  "nodes => nodes.every(n => { const b=n.getBoundingClientRect(); const c=n.closest('[data-testid=composer]').getBoundingClientRect(); return b.left >= c.left && b.right <= c.right; })"))
          .isEqualTo(true);
      page.screenshot(
          new Page.ScreenshotOptions().setPath(DIAGNOSTICS.resolve(prefix + "-" + width + ".png")));
    }
  }

  private static void attachThroughPicker(Page page, FilePayload payload) {
    if (!page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Attach files"))
        .isVisible())
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("File options")).click();
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
      Path root = Files.createTempDirectory("zalava-browser-file-");
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
