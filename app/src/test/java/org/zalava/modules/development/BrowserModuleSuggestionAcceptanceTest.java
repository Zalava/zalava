package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.support.ZalavaComponentTestConfiguration;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ZalavaComponentTestConfiguration.class)
class BrowserModuleSuggestionAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String PASSWORD = "SuggestionBrowserPassword-123";
  @LocalServerPort int port;
  @Autowired AccountLifecycle accounts;

  @TestBean(name = "deterministicChatModel", methodName = "scriptedModel", enforceOverride = true)
  ChatModel model;

  @TestBean(name = "moduleLocatorReleaseLocator", methodName = "catalog", enforceOverride = true)
  ModuleLocatorReleaseLocator locator;

  @TestBean(name = "moduleReleaseIndexRetrieval", methodName = "releases", enforceOverride = true)
  ModuleReleaseIndexRetrieval releases;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> "suggestion-browser-bootstrap");
    registry.add("zalava.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("spring.ai.model.chat", () -> "openai");
    registry.add("spring.ai.openai.api-key", () -> "local-scripted-suggestion-model");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void missingCapabilitySuggestionsLeadToActualCatalogReviewWithoutInstalling() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      for (AccountRole role : AccountRole.values()) {
        String login = "suggestion-browser-" + UUID.randomUUID();
        var account = accounts.create(login, PASSWORD, role);
        accounts.changePassword(account.id(), PASSWORD, PASSWORD + "-changed");
        try (var context = browser.newContext()) {
          Page page = context.newPage();
          page.navigate(base() + "/login");
          page.locator("input[name=username]").fill(login);
          page.locator("input[name=password]").fill(PASSWORD + "-changed");
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
          page.getByText("Connected", new Page.GetByTextOptions().setExact(true)).waitFor();
          page.locator("#message").fill("create job task");
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
              .click();
          page.locator(".message.assistant")
              .getByText(
                  "Possible existing modules", new Locator.GetByTextOptions().setExact(false))
              .waitFor();
          assertThat(page.locator(".message.assistant").innerText())
              .contains("Tasks", "Installation and loading have not happened");
          for (int width : new int[] {1440, 768, 390}) {
            page.setViewportSize(width, 900);
            if (width <= 1024)
              page.waitForFunction(
                  "getComputedStyle(document.querySelector('.zalava-navbar .navbar-menu')).visibility === 'hidden' && document.querySelector('.zalava-navbar .navbar-menu').getBoundingClientRect().right <= 0");
            assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth<=innerWidth"))
                .isTrue();
            Path dir = Path.of("build/browser-acceptance/module-suggestions");
            Files.createDirectories(dir);
            page.screenshot(
                new Page.ScreenshotOptions()
                    .setPath(dir.resolve("suggestion-" + role + "-" + width + ".png"))
                    .setFullPage(true));
          }
          if (role == AccountRole.ADMIN) {
            page.locator(".message.assistant a[href^='/modules?q=']").click();
            page.locator("[data-action=refresh-catalog]").click();
            // A refresh deliberately returns to the complete catalog; the suggested module remains
            // reviewable.
            page.locator("[data-catalog-module=zalava-module-tasks] [data-catalog-detail-link]")
                .click();
            assertThat(page.locator("[data-module-status]").innerText()).isEqualTo("Not installed");
            assertThat(page.locator("[data-action=request-installation]").isVisible()).isTrue();
          } else {
            assertThat(page.locator(".message.assistant").innerText())
                .contains("Ask an administrator");
            assertThat(page.locator(".message.assistant a[href^='/modules']").count()).isZero();
          }
        }
      }
    }
  }

  private String base() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-browser-module-suggestions-");
      Files.writeString(root.resolve("AGENT.md"), "Suggestion browser workspace.");
      return root;
    } catch (IOException failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  static ModuleLocatorReleaseLocator catalog() {
    return new ModuleLocatorReleaseLocator() {
      public List<Module> modules() {
        return ModuleSuggestionComponentTest.catalog().modules();
      }

      public ResolvedModule resolve(String id) {
        return new ResolvedModule(
            id,
            id,
            "fixture",
            URI.create("https://example.test/" + id + "/releases/index.yaml"),
            URI.create("https://example.test/maven"));
      }
    };
  }

  static ModuleReleaseIndexRetrieval releases() {
    return (uri, token) ->
        new ModuleReleaseIndex(
            1,
            uri.getPath().split("/")[1],
            List.of(
                new ModuleReleaseIndex.Release(
                    "1.0.0",
                    "v1.0.0",
                    new ModuleReleaseIndex.Artifact(
                        "org.example", "module", "1.0.0", "a".repeat(64)),
                    new ModuleReleaseIndex.Source(
                        URI.create("https://github.com/example/module"), "Apache-2.0"),
                    new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
                    new ModuleReleaseIndex.Security(List.of()))));
  }

  static ChatModel scriptedModel() {
    ChatModel delegate = ModuleSuggestionComponentTest.scriptedModel();
    return new ChatModel() {
      public ChatOptions getOptions() {
        return delegate.getOptions();
      }

      public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
      }

      public ChatResponse call(Prompt prompt) {
        if (prompt.getLastUserOrToolResponseMessage() instanceof ToolResponseMessage response) {
          var data = new ObjectMapper().readTree(response.getResponses().getFirst().responseData());
          assertThat(data.path("status").asString()).isEqualTo("CATALOG_MATCH");
          var module = data.path("modules").get(0);
          String name = module.path("displayName").asString();
          String label =
              module.has("reviewUrl")
                  ? "[" + name + "](" + module.path("reviewUrl").asString() + ")"
                  : name;
          String text =
              "### Possible existing modules\n\n- "
                  + label
                  + ": "
                  + module.path("description").asString()
                  + "\n\n"
                  + data.path("nextAction").asString()
                  + "\n\nInstallation and loading have not happened.";
          return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
        return delegate.call(prompt);
      }
    };
  }
}
