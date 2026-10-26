package org.zalava.web.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import org.zalava.api.InvocationContext;
import org.zalava.capabilities.discovery.adapter.out.springai.ZalavaToolCallbackNames;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  BrowserMemberToolAccessAcceptanceTest.ClockModelConfiguration.class,
  MemberToolAccessFixture.class
})
class BrowserMemberToolAccessAcceptanceTest {
  static final Path WORKSPACE = workspace();
  static final String PASSWORD = "ToolAccessBrowserPassword-123";
  @LocalServerPort int port;
  @Autowired AccountLifecycle accounts;
  @Autowired ProviderToolOperations operations;
  @Autowired RestartProgressController restart;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("spring.ai.model.chat", () -> "openai");
    registry.add("spring.ai.openai.api-key", () -> "scripted-clock-fixture");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void administratorGrantsAndRevokesOneMembersToolThroughRealForms() throws Exception {
    var admin = account(AccountRole.ADMIN);
    var member = account(AccountRole.MEMBER);
    var other = account(AccountRole.MEMBER);
    try (var playwright = Playwright.create();
        var browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        var context = browser.newContext()) {
      var page = context.newPage();
      signIn(page, admin);
      page.navigate(base() + "/zalava/accounts");
      page.locator("[data-account-id='" + member.id() + "']")
          .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Manage tool access"))
          .click();
      var row = page.locator("[data-tool-access='member-clock:current_time']");
      assertThat(row).containsText("Not allowed");
      row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Grant access"))
          .click();
      assertThat(page.getByRole(AriaRole.STATUS)).containsText("Tool access granted");
      assertThat(row).containsText("Allowed");
      assertThat(invoke(member).status())
          .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
      assertThatThrownBy(() -> invoke(other)).hasMessageContaining("denied");
      page.reload();
      assertThat(row).containsText("Allowed");
      Files.createDirectories(Path.of("build/browser-acceptance/module-access"));
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        settleLayout(page);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/module-access/tools-" + width + ".png"))
                .setFullPage(true));
        assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
            .isTrue();
      }
      try (var clockContext = browser.newContext()) {
        var clockPage = clockContext.newPage();
        signIn(clockPage, member);
        clockPage.locator("#message").fill("Use current time in UTC");
        clockPage
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        assertThat(clockPage.locator(".messages")).containsText("Clock result verified");
        clockPage
            .getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Tools").setExact(true))
            .click();
        var toolCall =
            clockPage
                .locator("#inspector-panel details")
                .filter(new Locator.FilterOptions().setHasText("current_time"));
        assertThat(toolCall).hasCount(1);
        toolCall.locator("summary").click();
        assertThat(toolCall).containsText("UTC");
        assertThat(toolCall).containsText("2026-10-26T12:00:00Z");
        for (int width : new int[] {1440, 768, 390}) {
          clockPage.setViewportSize(width, 900);
          settleLayout(clockPage);
          if (width <= 768
              && clockPage.locator(".inspector-toggle").isVisible()
              && "false"
                  .equals(clockPage.locator(".inspector-toggle").getAttribute("aria-expanded")))
            clockPage.locator(".inspector-toggle").click();
          assertThat(clockPage.locator("#inspector-panel")).isVisible();
          clockPage.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(
                      Path.of(
                          "build/browser-acceptance/module-access/chat-tools-" + width + ".png")));
        }
      }
      row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Revoke access"))
          .focus();
      page.keyboard().press("Enter");
      assertThat(page.getByRole(AriaRole.STATUS)).containsText("Tool access revoked");
      assertThatThrownBy(() -> invoke(member)).hasMessageContaining("denied");
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/module-access/revoked-390.png")));
      try (var memberContext = browser.newContext()) {
        var memberPage = memberContext.newPage();
        signIn(memberPage, member);
        memberPage.navigate(base() + "/zalava/accounts/" + member.id() + "/tools");
        assertThat(memberPage.getByText("Error 403", new Page.GetByTextOptions().setExact(true)))
            .isVisible();
      }
      page.navigate(base() + "/modules");
      assertThat(page.locator("[data-module='member-access-fixture']")).hasCount(1);
      assertThat(
              page.getByRole(
                  AriaRole.HEADING,
                  new Page.GetByRoleOptions().setName("Enabled external modules")))
          .hasCount(0);
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        settleLayout(page);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of("build/browser-acceptance/module-access/modules-" + width + ".png"))
                .setFullPage(true));
        page.navigate(base() + "/monitoring");
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of("build/browser-acceptance/module-access/monitoring-" + width + ".png"))
                .setFullPage(true));
        page.navigate(base() + "/modules");
      }
    }
  }

  @Test
  void restartProgressWaitsThroughDisconnectAndRedirectsOnlyAfterReady() throws Exception {
    try (var playwright = Playwright.create();
        var browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        var context = browser.newContext()) {
      var page = context.newPage();
      var polls = new AtomicInteger();
      page.route(
          "**/restart-status?*",
          route -> {
            int attempt = polls.incrementAndGet();
            route.fulfill(
                new Route.FulfillOptions()
                    .setStatus(attempt == 1 ? 503 : 200)
                    .setContentType("application/json")
                    .setBody(
                        attempt < 6
                            ? "{\"ready\":false,\"failed\":false}"
                            : "{\"ready\":true,\"failed\":false}"));
          });
      page.navigate(base() + "/restart-progress?previous=" + restart.generation());
      assertThat(
              page.getByRole(
                  AriaRole.HEADING, new Page.GetByRoleOptions().setName("Restarting Zalava")))
          .isVisible();
      Files.createDirectories(Path.of("build/browser-acceptance/module-access"));
      page.setViewportSize(390, 900);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/module-access/restarting-390.png")));
      for (int width : new int[] {1440, 768}) {
        page.setViewportSize(width, 900);
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of(
                        "build/browser-acceptance/module-access/restarting-" + width + ".png")));
      }
      page.waitForURL("**/login");
      assertThat(polls.get()).isGreaterThanOrEqualTo(6);
      page.unroute("**/restart-status?*");
      page.route(
          "**/restart-status?*",
          route ->
              route.fulfill(
                  new Route.FulfillOptions()
                      .setStatus(200)
                      .setContentType("application/json")
                      .setBody("{\"ready\":false,\"failed\":true}")));
      page.navigate(base() + "/restart-progress?previous=" + restart.generation());
      assertThat(page.getByRole(AriaRole.STATUS)).containsText("could not complete the restart");
      assertThat(
              page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Check again")))
          .isVisible();
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(Path.of("build/browser-acceptance/module-access/restart-error-390.png")));
    }
  }

  private ProviderToolOperations.ToolInvocationOutcome invoke(Account account) {
    return operations.invoke(
        ProviderToolOperations.ToolInvocationCommand.operator(
            "member-clock",
            "current_time",
            "{}",
            new InvocationContext(
                account.id().toString(), false, Map.of("accountRole", "MEMBER"))));
  }

  private Account account(AccountRole role) {
    var a = accounts.create("browser-access-" + UUID.randomUUID(), PASSWORD, role);
    accounts.changePassword(a.id(), PASSWORD, PASSWORD + "-changed");
    return a;
  }

  private void signIn(Page page, Account account) {
    page.navigate(base() + "/login");
    page.locator("input[name=username]").fill(account.loginName());
    page.locator("input[name=password]").fill(PASSWORD + "-changed");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.waitForURL("**/chat");
  }

  private static void settleLayout(Page page) {
    page.waitForFunction(
        "() => innerWidth > 1024 || (() => {const menu=document.querySelector('#zalava-product-navigation');return !menu || (getComputedStyle(menu).visibility==='hidden' && menu.getBoundingClientRect().right<=1);})()");
    page.waitForFunction(
        "() => document.getAnimations().every(animation => animation.playState !== 'running')");
  }

  private String base() {
    return "http://127.0.0.1:" + port;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockModelConfiguration {
    @Bean
    @Primary
    ChatModel clockTestModel() {
      return new ChatModel() {
        public ChatOptions getOptions() {
          return ToolCallingChatOptions.builder().build();
        }

        public ChatResponse call(Prompt prompt) {
          AssistantMessage response;
          if (prompt.getInstructions().getLast() instanceof ToolResponseMessage) {
            response = new AssistantMessage("Clock result verified");
          } else {
            String name = ZalavaToolCallbackNames.forTool("member-clock", "current_time");
            var options = (ToolCallingChatOptions) prompt.getOptions();
            if (options.getToolCallbacks().stream()
                .anyMatch(callback -> callback.getToolDefinition().name().equals(name)))
              response =
                  AssistantMessage.builder()
                      .toolCalls(
                          List.of(
                              new AssistantMessage.ToolCall(
                                  "clock-call", "function", name, "{\"zone\":\"UTC\"}")))
                      .build();
            else response = new AssistantMessage("Tool unavailable");
          }
          return new ChatResponse(List.of(new Generation(response)));
        }

        public Flux<ChatResponse> stream(Prompt prompt) {
          return Flux.defer(() -> Flux.just(call(prompt))).subscribeOn(Schedulers.boundedElastic());
        }
      };
    }
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-member-tools-browser-");
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
