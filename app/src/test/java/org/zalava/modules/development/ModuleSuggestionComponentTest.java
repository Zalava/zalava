package org.zalava.modules.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import org.zalava.support.SecureZalavaComponentTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SecureZalavaComponentTest
@TestPropertySource(
    properties = {"zalava.test.chat-provider=openai", "zalava.test.context=module-suggestions"})
class ModuleSuggestionComponentTest {
  private static final AtomicReference<String> MODE = new AtomicReference<>("matches");

  @TestBean(name = "deterministicChatModel", methodName = "scriptedModel", enforceOverride = true)
  ChatModel model;

  @TestBean(name = "moduleLocatorReleaseLocator", methodName = "catalog", enforceOverride = true)
  ModuleLocatorReleaseLocator locator;

  @Autowired ActorChatCommands chat;
  @Autowired AccountLifecycle accounts;

  @Autowired
  @Qualifier("rawModuleReleaseInstallation")
  ModuleReleaseInstallation installations;

  @Autowired ModuleQueries modules;
  @Autowired ObjectMapper json;

  @AfterEach
  void resetCatalog() {
    MODE.set("matches");
  }

  @Test
  void missingJobsSuggestExistingModulesBeforeDevelopmentForAdministrator() throws Exception {
    JsonNode result = turn(AccountRole.ADMIN, "create job task");
    assertThat(result.path("status").asString()).isEqualTo("CATALOG_MATCH");
    assertThat(result.path("modules").get(0).path("moduleId").asString())
        .isEqualTo("zalava-module-tasks");
    assertThat(result.path("modules").get(0).path("reviewUrl").asString())
        .startsWith("/modules?q=");
    assertThat(result.path("nextAction").asString())
        .contains("Check catalog", "administrator", "Install");
    assertThat(result.toString()).doesNotContain("manual_module_development");
  }

  @Test
  void memberGetsAdministratorHandoffWithoutAnInaccessibleInstallLink() throws Exception {
    JsonNode result = turn(AccountRole.MEMBER, "read PDF document");
    assertThat(result.path("status").asString()).isEqualTo("CATALOG_MATCH");
    assertThat(result.path("modules").get(0).path("moduleId").asString())
        .isEqualTo("zalava-module-tika");
    assertThat(result.path("nextAction").asString()).contains("Ask an administrator");
    assertThat(result.toString()).doesNotContain("reviewUrl", "manual_module_development");
  }

  @Test
  void severalPossibleModulesRemainRecommendationsWithoutInstallation() throws Exception {
    JsonNode result = turn(AccountRole.ADMIN, "schedule job and read PDF");
    assertThat(result.path("modules").size()).isEqualTo(2);
    assertThat(result.path("matching").asString()).contains("metadata", "verify");
    assertThat(result.path("automaticActions")).isEmpty();
  }

  @Test
  void catalogOutageCannotBecomeAFabricatedNoMatchOrDevelopmentRecommendation() throws Exception {
    MODE.set("outage");
    JsonNode result = turn(AccountRole.ADMIN, "create job task");
    assertThat(result.path("status").asString()).isEqualTo("CATALOG_UNAVAILABLE");
    assertThat(result.path("nextAction").asString()).contains("retry");
    assertThat(result.toString())
        .doesNotContain("manual_module_development", "secret-upstream-detail");
  }

  @Test
  void genuineNoMatchKeepsDevelopmentExplicitAndReviewable() throws Exception {
    MODE.set("empty");
    JsonNode result = turn(AccountRole.MEMBER, "pollen forecast");
    assertThat(result.path("status").asString()).isEqualTo("REVIEW_REQUIRED");
    assertThat(result.path("catalogStatus").asString()).isEqualTo("NO_MATCH");
    assertThat(result.path("recommendedWorkflow").asString())
        .isEqualTo("manual_module_development");
    assertThat(result.path("confirmationRequired").asBoolean()).isTrue();
  }

  private JsonNode turn(AccountRole role, String input) throws Exception {
    var account =
        accounts.create("suggestion-" + UUID.randomUUID(), "SuggestionPassword-123", role);
    accounts.changePassword(
        account.id(), "SuggestionPassword-123", "ChangedSuggestionPassword-123");
    Actor actor = new Actor(account.id());
    var before = installations.recent(100);
    var enabled = modules.enabledModules();
    var reference = chat.createWebConversation(actor);
    String answer = chat.chat(actor, reference, input).text();
    assertThat(installations.recent(100)).isEqualTo(before);
    assertThat(modules.enabledModules()).isEqualTo(enabled);
    return json.readTree(answer);
  }

  static ModuleLocatorReleaseLocator catalog() {
    return new ModuleLocatorReleaseLocator() {
      public List<Module> modules() {
        if (MODE.get().equals("outage")) throw new IllegalStateException("secret-upstream-detail");
        if (MODE.get().equals("empty")) return List.of();
        return List.of(
            new Module("zalava-module-tasks", "Tasks", "Create task and schedule job work."),
            new Module(
                "zalava-module-tika", "Tika", "Read PDF document content and extract text."));
      }

      public ResolvedModule resolve(String id) {
        throw new AssertionError("Suggestions must not resolve or download artifacts");
      }
    };
  }

  static ChatModel scriptedModel() {
    return new ChatModel() {
      public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
      }

      public ChatResponse call(Prompt prompt) {
        if (prompt.getLastUserOrToolResponseMessage() instanceof ToolResponseMessage message)
          return new ChatResponse(
              List.of(
                  new Generation(
                      new AssistantMessage(message.getResponses().getFirst().responseData()))));
        String input = prompt.getUserMessage().getText().lines().findFirst().orElse("");
        var call =
            new AssistantMessage.ToolCall(
                "gap-call",
                "function",
                "recommendModuleDevelopment",
                new ObjectMapper()
                    .writeValueAsString(Map.of("capability", input, "userRequest", input)));
        return new ChatResponse(
            List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build())));
      }
    };
  }
}
