package org.zalava.assistant.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.chat.ChatChannel;
import org.zalava.assistant.chat.ChatTurnResult;
import org.zalava.capabilities.discovery.adapter.out.springai.SeaToolCallbackNames;
import org.zalava.modules.runtime.SeaRuntime;
import reactor.core.publisher.Flux;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ChatControllerComponentTest.ChatModelTestConfiguration.class)
class ChatControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Autowired private ChatChannel chatChannel;

  @Autowired private ChatMemoryRepository chatMemoryRepository;

  @Autowired private CapturingChatModel chatModel;

  @Autowired private SeaRuntime seaRuntime;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void rendersChatPageInSeaProductShell() throws Exception {
    mockMvc
        .perform(get("/chat"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Chat</title>")))
        .andExpect(content().string(containsString("id=\"root\"")))
        .andExpect(content().string(containsString("/sea-chat/assets/sea-chat.js")))
        .andExpect(content().string(containsString("href=\"/jobs\"")));
    mockMvc.perform(get("/sea-chat/assets/sea-chat.js")).andExpect(status().isOk());
  }

  @Test
  void completesChatTurnWithUniqueRequestScopedToolsAndPersistsMemory() {
    ChatTurnResult result = chatChannel.chat("component-chat", "hi");

    assertThat(result.text()).isEqualTo("Hello from the component test model.");
    assertThat(result.jobReferences()).isEmpty();

    Prompt prompt = chatModel.lastPrompt();
    assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEqualTo("hi");
    String normalizedInstructions =
        prompt.getInstructions().stream()
            .map(message -> message.getText())
            .reduce("", (left, right) -> left + " " + right)
            .replaceAll("\\s+", " ");
    assertThat(normalizedInstructions)
        .contains("SEA provider tool grounding")
        .contains("Do not answer provider-state questions from chat memory alone")
        .contains("translate the provider/tool search query to likely English metadata terms")
        .contains("recommendModuleDevelopment")
        .contains("only after an explicit yes")
        .contains(
            "claim that a side-effecting provider action is complete unless the "
                + "provider tool result confirms execution")
        .contains("If no provider tool was invoked, say that you cannot confirm");
    assertThat(prompt.getOptions())
        .isInstanceOfSatisfying(
            ToolCallingChatOptions.class,
            options -> {
              List<String> toolNames =
                  options.getToolCallbacks().stream()
                      .map(callback -> callback.getToolDefinition().name())
                      .toList();
              assertThat(toolNames)
                  .contains(
                      "searchSeaProviderTools", "loadSeaProviderTool", "invokeSeaProviderTool")
                  .doesNotContain("createTask", "scheduleTask", "CheckListTool")
                  .doesNotHaveDuplicates();
            });
    assertThat(chatMemoryRepository.findByConversationId("component-chat"))
        .containsExactly(
            new UserMessage("hi"), new AssistantMessage("Hello from the component test model."));
  }

  @Test
  void createsStructuredModuleDevelopmentRequestThroughChat() {
    ChatTurnResult result = chatChannel.chat("component-weather-module", "create weather module");

    assertThat(result.text()).contains("sea-weather-module", "PREPARED");
    Prompt prompt = chatModel.lastPrompt();
    assertThat(prompt.getOptions())
        .isInstanceOfSatisfying(
            ToolCallingChatOptions.class,
            options ->
                assertThat(options.getToolCallbacks())
                    .filteredOn(
                        callback ->
                            callback
                                .getToolDefinition()
                                .name()
                                .equals("createModuleDevelopmentRequest"))
                    .singleElement()
                    .satisfies(
                        callback ->
                            assertThat(callback.getToolDefinition().inputSchema())
                                .contains("contract", "moduleId", "tools")));
  }

  @Test
  void registersAndInvokesLoadedTimeProviderThroughChat() {
    ChatTurnResult result = chatChannel.chat("component-time", "what time is it now");

    assertThat(result.text()).contains("12:34:56Z");
    Prompt prompt = chatModel.lastPrompt();
    assertThat(prompt.getOptions())
        .isInstanceOfSatisfying(
            ToolCallingChatOptions.class,
            options ->
                assertThat(options.getToolCallbacks())
                    .extracting(callback -> callback.getToolDefinition().name())
                    .contains(SeaToolCallbackNames.forTool("time-provider", "current_time")));
  }

  @Test
  void hasNoCompatibilityModulesOrCallbacks() {
    assertThat(seaRuntime.modules())
        .extracting(module -> module.descriptor().moduleId())
        .doesNotContain("sea-legacy-tools", "sea-filesystem", "sea-web-search", "sea-web-browser");
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("chat-controller-component-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Files.writeString(workspace.resolve("module-probe.txt"), "provided filesystem module");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal skill for component test context startup.
                    ---

                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ChatModelTestConfiguration {

    @Bean
    @Primary
    CapturingChatModel capturingChatModel() {
      return new CapturingChatModel();
    }

    @Bean
    org.zalava.api.ZalavaModule timeModule() {
      org.zalava.api.ZalavaProvider provider =
          new org.zalava.api.ZalavaProvider() {
            @Override
            public org.zalava.api.ProviderDescriptor descriptor() {
              return new org.zalava.api.ProviderDescriptor(
                  "time-provider",
                  "test-time",
                  "time",
                  "Test Time",
                  "Current time provider",
                  "1.0.0",
                  org.zalava.api.ProviderCapabilities.toolsOnly(),
                  List.of(),
                  java.util.Map.of());
            }

            @Override
            public org.zalava.api.ProviderCapabilities capabilities() {
              return org.zalava.api.ProviderCapabilities.toolsOnly();
            }

            @Override
            public List<org.zalava.api.ZalavaToolDescriptor> listTools() {
              return List.of(
                  new org.zalava.api.ZalavaToolDescriptor(
                      "current_time",
                      "Returns the current time.",
                      false,
                      List.of(),
                      java.util.Map.of("type", "object")));
            }

            @Override
            public org.zalava.api.ZalavaOperationResult callTool(
                String toolName,
                java.util.Map<String, Object> argumentValues,
                org.zalava.api.InvocationContext context) {
              tools.jackson.databind.JsonNode arguments =
                  new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
              return org.zalava.api.ZalavaOperationResult.success(
                  java.util.Map.of("time", "12:34:56Z"));
            }
          };
      return new org.zalava.api.ZalavaModule() {
        @Override
        public org.zalava.api.ModuleDescriptor descriptor() {
          return new org.zalava.api.ModuleDescriptor(
              "test-time", "1.0.0", "Test Time", "Test time module");
        }

        @Override
        public List<org.zalava.api.ProviderFactory> providerFactories() {
          return List.of(
              new org.zalava.api.ProviderFactory() {
                @Override
                public org.zalava.api.ProviderFactoryDescriptor descriptor() {
                  return new org.zalava.api.ProviderFactoryDescriptor(
                      "time-factory", "test-time", "time", "Test Time", "Test time provider");
                }

                @Override
                public List<org.zalava.api.ZalavaProvider> createProviders(
                    org.zalava.api.ProviderFactoryContext context) {
                  return List.of(provider);
                }
              });
        }
      };
    }
  }

  static final class CapturingChatModel implements ChatModel {

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
    private final ChatOptions defaultOptions = ToolCallingChatOptions.builder().build();

    @Override
    public ChatResponse call(Prompt prompt) {
      lastPrompt.set(prompt);
      if (prompt.getLastUserOrToolResponseMessage() instanceof ToolResponseMessage toolResponse) {
        String response = toolResponse.getResponses().getFirst().responseData();
        return response(response);
      }
      return switch (originalUserInput(prompt)) {
        case "what time is it now" ->
            toolCall(SeaToolCallbackNames.forTool("time-provider", "current_time"), "{}");
        case "render markdown" ->
            response("**Bold result**\n\n```java\nSystem.out.println(\"safe\");\n```");
        case "fail chat" -> throw new IllegalStateException("Scripted chat failure");
        case "create weather module" ->
            toolCall(
                "createModuleDevelopmentRequest",
                """
                                {"contract":{"module":{"moduleId":"sea-weather-module","versionPolicy":"1.0.0"},"purpose":"Provide current weather information for a requested location","targetSeaApiVersion":"1.0.0","tools":[{"name":"current_weather","description":"Returns current weather for a requested location","inputSchema":"{\\"type\\":\\"object\\",\\"required\\":[\\"location\\"],\\"properties\\":{\\"location\\":{\\"type\\":\\"string\\"}}}","outputSchema":"{\\"type\\":\\"object\\",\\"required\\":[\\"location\\",\\"condition\\",\\"temperatureC\\"],\\"properties\\":{\\"location\\":{\\"type\\":\\"string\\"},\\"condition\\":{\\"type\\":\\"string\\"},\\"temperatureC\\":{\\"type\\":\\"number\\"}}}","knownErrorCodes":["INVALID_INPUT","PROVIDER_UNAVAILABLE"],"examples":[{"inputJson":"{\\"location\\":\\"Madrid\\"}","expectedOutputJson":"{}"}]}],"expectedErrors":[{"code":"INVALID_INPUT","description":"Location input is missing or invalid"}],"acceptanceScenarios":[{"id":"current-weather","requestJson":"{\\"location\\":\\"Madrid\\"}","assertions":[{"path":"$.location","type":"exists","expectedValueJson":"true"}]}],"operationalRequirements":{"timeoutMs":10000,"maximumResponseBytes":100000},"deliveryRequirements":{"packageFormat":"jar","fileNamePattern":"sea-weather-module-*.jar","requiredManifestVersion":"1","multipleArtifactsAllowed":false,"requiredMetadata":{}}},"reason":"Provide current weather information"}
                                """);
        default -> response("Hello from the component test model.");
      };
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      if (originalUserInput(prompt).equals("slow response")) {
        return Flux.just(response("Eventually complete.")).delayElements(Duration.ofMillis(300));
      }
      return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getOptions() {
      return defaultOptions;
    }

    Prompt lastPrompt() {
      return lastPrompt.get();
    }

    private static String originalUserInput(Prompt prompt) {
      return prompt.getUserMessage().getText().lines().findFirst().orElse("");
    }

    private static ChatResponse toolCall(String name, String arguments) {
      AssistantMessage.ToolCall toolCall =
          new AssistantMessage.ToolCall("component-" + name, "function", name, arguments);
      return new ChatResponse(
          List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(toolCall)).build())));
    }

    private static ChatResponse response(String content) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
  }
}
