package org.zalava.web.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
          .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Manage permissions"))
          .click();
      assertThat(page.locator("[data-permission-tool='member-clock:current_time']"))
          .containsText("Not allowed");
      page.navigate(base() + "/zalava/accounts/" + member.id() + "/tools");
      var row = page.locator("[data-tool-access='member-clock:current_time']");
      assertThat(row).containsText("Not allowed");
      row.locator("select[name=decision]").selectOption("ALLOW");
      row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save override"))
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
        assertThat((Boolean) row.evaluate("e => e.getBoundingClientRect().height < 360")).isTrue();
        assertThat(
                (Boolean)
                    row.locator(".tool-access-copy")
                        .evaluate("e => e.clientWidth>=Math.min(240,innerWidth-80)"))
            .isTrue();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(Path.of("build/browser-acceptance/module-access/tools-" + width + ".png"))
                .setFullPage(true));
        assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
            .isTrue();
      }
      try (var clockContext = browser.newContext()) {
        var clockPage = clockContext.newPage();
        List<String> browserErrors = new ArrayList<>();
        clockPage.onPageError(browserErrors::add);
        // Only elapsed metrics use fixed values here; call identity/input/result come from the real
        // host.
        clockPage.route(
            "**/chat/*/evidence",
            route -> {
              var response = route.fetch();
              var body = new ObjectMapper().readTree(response.text());
              body.path("runs").forEach(run -> ((ObjectNode) run).put("durationMillis", 123045));
              body.path("tools").forEach(tool -> ((ObjectNode) tool).put("durationMillis", 12285));
              route.fulfill(
                  new Route.FulfillOptions()
                      .setStatus(response.status())
                      .setContentType("application/json")
                      .setBody(body.toString()));
              response.dispose();
            });
        signIn(clockPage, member);
        clockPage.locator("#message").fill("Use current time in UTC");
        clockPage
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        assertThat(clockPage.locator(".messages")).containsText("Clock result verified");
        var answer = clockPage.locator(".message.assistant").last();
        assertThat(answer.locator(".answer-timing")).hasText("2 min 3 s 45 ms");
        assertThat(answer.locator(".answer-execution")).not().hasAttribute("open", "");
        answer.locator(".answer-execution > summary").click();
        var toolCall = answer.locator(".answer-tool");
        assertThat(toolCall).hasCount(1);
        assertThat(toolCall.locator("summary")).containsText("12 s 285 ms");
        toolCall.locator("summary").click();
        assertThat(toolCall).containsText("UTC");
        assertThat(toolCall).containsText("2026-10-26T12:00:00Z");
        String firstRun = answer.locator("[data-run-id]").getAttribute("data-run-id");
        clockPage.locator("#message").fill("Use current time in UTC");
        clockPage
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        assertThat(clockPage.locator(".message.assistant")).hasCount(2);
        assertThat(clockPage.locator(".message.assistant").last().locator(".answer-timing"))
            .not()
            .containsText("unavailable");
        String secondRun =
            clockPage
                .locator(".message.assistant")
                .last()
                .locator("[data-run-id]")
                .getAttribute("data-run-id");
        assertThat(secondRun).isNotBlank().isNotEqualTo(firstRun);
        clockPage.reload();
        assertThat(clockPage.locator(".message.assistant")).hasCount(2);
        assertThat(clockPage.locator(".message.assistant").first().locator("[data-run-id]"))
            .hasAttribute("data-run-id", firstRun);
        assertThat(clockPage.locator(".message.assistant").last().locator("[data-run-id]"))
            .hasAttribute("data-run-id", secondRun);
        clockPage
            .locator(".message.assistant")
            .last()
            .locator(".answer-execution > summary")
            .click();
        clockPage.locator(".message.assistant").last().locator(".answer-tool > summary").click();
        for (int width : new int[] {1440, 768, 390}) {
          clockPage.setViewportSize(width, 900);
          settleLayout(clockPage);
          assertThat(clockPage.locator("#chat-inspector")).not().isVisible();
          assertThat(
                  (Boolean) clockPage.evaluate("document.documentElement.scrollWidth<=innerWidth"))
              .isTrue();
          clockPage.screenshot(
              new Page.ScreenshotOptions()
                  .setPath(
                      Path.of(
                          "build/browser-acceptance/module-access/chat-tools-" + width + ".png")));
        }
        clockPage.locator("#message").fill("Trigger a scripted provider failure");
        clockPage
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
            .click();
        var failedAnswer = clockPage.locator(".message.failure").last();
        assertThat(failedAnswer).containsText("could not complete");
        assertThat(failedAnswer.locator(".answer-timing")).not().containsText("unavailable");
        failedAnswer.locator(".answer-execution > summary").click();
        assertThat(failedAnswer).containsText("Failed");
        assertThat(browserErrors).isEmpty();
      }
      row.locator("select[name=decision]").selectOption("DENY");
      row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save override"))
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
      page.navigate(base() + "/zalava/accounts/tool-defaults");
      var defaultRow = page.locator("[data-tool-access='member-clock:current_time']");
      defaultRow.locator("select[name=membersAllowed]").selectOption("true");
      defaultRow
          .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save default"))
          .click();
      assertThat(invoke(other).status())
          .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
      assertThatThrownBy(() -> invoke(member)).hasMessageContaining("denied");
      for (int width : new int[] {1440, 768, 390}) {
        page.setViewportSize(width, 900);
        settleLayout(page);
        assertThat((Boolean) defaultRow.evaluate("e => e.getBoundingClientRect().height < 360"))
            .isTrue();
        page.screenshot(
            new Page.ScreenshotOptions()
                .setPath(
                    Path.of("build/browser-acceptance/module-access/defaults-" + width + ".png"))
                .setFullPage(true));
      }
      page.navigate(base() + "/zalava/accounts/" + member.id() + "/tools");
      row.locator("select[name=decision]").selectOption("INHERIT");
      row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save override"))
          .click();
      assertThat(row).containsText("Role default");
      assertThat(invoke(member).status())
          .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
      page.navigate(base() + "/zalava/accounts/tool-defaults");
      defaultRow.locator("select[name=membersAllowed]").selectOption("false");
      defaultRow
          .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save default"))
          .click();
      assertThatThrownBy(() -> invoke(member)).hasMessageContaining("denied");
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
          if (prompt
              .getInstructions()
              .getLast()
              .getText()
              .contains("Trigger a scripted provider failure"))
            throw new IllegalStateException("Scripted provider failure");
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
