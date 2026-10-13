package org.zalava.assistant.models.configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.application.port.out.AgentModel;
import org.zalava.assistant.chat.api.ChatProviderReadiness;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatStreamListener;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.AuthenticatedMockMvcTestConfiguration;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.ZalavaComponentTestConfiguration;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({AuthenticatedMockMvcTestConfiguration.class, ZalavaComponentTestConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LiveProviderComponentTest {
  private static final Path WORKSPACE = workspace();
  @Autowired MockMvc mvc;
  @Autowired ChatProviderReadiness readiness;
  @Autowired ActorChatCommands chat;

  @Autowired
  @Qualifier("springAiAgentModel")
  AgentModel agentModel;

  @Autowired AccountLifecycle accounts;
  ModelProviderStore store = new ModelProviderStore(WORKSPACE);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("zalava.accounts.security-enabled", () -> "true");
    registry.add("zalava.accounts.bootstrap-login", () -> "live-provider-admin");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    PostgreSqlTestDatabase.register(registry);
  }

  @AfterEach
  void removeSettings() throws IOException {
    Files.deleteIfExists(store.path());
  }

  @Test
  void savingEnablesChatAndSwitchesActualProviderAndModelWithoutRestart() throws Exception {
    assertThat(readiness.configured()).isFalse();
    WireMockServer server = new WireMockServer(0);
    server.start();
    try {
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/chat/completions"))
              .willReturn(okJson(openai("OpenAI is active."))));
      save("openai-compatible", "first-model", server.baseUrl() + "/v1");
      assertThat(readiness.configured()).isTrue();
      assertThat(turn("First question")).isEqualTo("OpenAI is active.");
      save("openai-compatible", "second-model", server.baseUrl() + "/v1");
      assertThat(turn("Second question")).isEqualTo("OpenAI is active.");
      server.verify(
          postRequestedFor(urlEqualTo("/v1/chat/completions"))
              .withRequestBody(matchingJsonPath("$.model", equalTo("second-model"))));
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/messages"))
              .willReturn(
                  okJson(
                      "{\"id\":\"fixture\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"third-model\",\"content\":[{\"type\":\"text\",\"text\":\"Anthropic is active.\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}")));
      save("anthropic", "third-model", server.baseUrl());
      assertThat(turn("Third question")).isEqualTo("Anthropic is active.");
      server.verify(
          postRequestedFor(urlEqualTo("/v1/messages"))
              .withRequestBody(matchingJsonPath("$.model", equalTo("third-model"))));
      String settings =
          mvc.perform(get("/settings").param("section", "provider"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(settings)
          .contains("Anthropic")
          .doesNotContain(
              "Restart required",
              "fixture-secret-key",
              "Saved changes apply after restarting Zalava",
              "Restart Zalava, then try a message");
    } finally {
      server.stop();
    }
  }

  @Test
  void failedClientPreparationPreservesTheCurrentProviderAndSavedSettings() throws Exception {
    WireMockServer server = new WireMockServer(0);
    server.start();
    try {
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/chat/completions"))
              .willReturn(okJson(openai("Previous provider still works."))));
      save("openai-compatible", "working-model", server.baseUrl() + "/v1");
      Map<String, Object> previous = store.read();
      mvc.perform(
              post("/settings/provider")
                  .param("provider", "google-vertex")
                  .param("model", "vertex-model")
                  .param("projectId", "fixture-project")
                  .param("location", "us-central1")
                  .param(
                      "credentialsUri",
                      WORKSPACE.resolve("missing-google-credentials.json").toUri().toString()))
          .andExpect(status().is3xxRedirection())
          .andExpect(
              flash().attribute("settingsError", containsString("current provider is unchanged")));
      assertThat(store.read()).isEqualTo(previous);
      assertThat(turn("Does the old provider still work?"))
          .isEqualTo("Previous provider still works.");
    } finally {
      server.stop();
    }
  }

  @Test
  void inFlightNativeStreamCompletesOnItsOriginalGenerationWhileNewRequestsSwitch()
      throws Exception {
    WireMockServer server = new WireMockServer(0);
    server.start();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      String stream =
          "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"first-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Old \"},\"finish_reason\":null}]}\n\n"
              + "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"first-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"stream completes.\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/chat/completions"))
              .atPriority(1)
              .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
              .willReturn(
                  WireMock.aResponse()
                      .withHeader("Content-Type", "text/event-stream")
                      .withBody(stream)
                      .withChunkedDribbleDelay(4, 3000)));
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/chat/completions"))
              .atPriority(10)
              .willReturn(okJson(openai("New provider request."))));
      save("openai-compatible", "first-model", server.baseUrl() + "/v1");
      var account =
          accounts.create(
              "live-stream-" + UUID.randomUUID(), "StreamPassword-123", AccountRole.MEMBER);
      accounts.changePassword(account.id(), "StreamPassword-123", "ChangedStreamPassword-123");
      Actor actor = new Actor(account.id());
      var conversation = chat.createWebConversation(actor);
      CountDownLatch firstDelta = new CountDownLatch(1);
      var future =
          executor.submit(
              () ->
                  chat.streamChat(
                      actor,
                      conversation,
                      "Keep this stream alive",
                      new ActorChatStreamListener() {
                        public void onDelta(String text) {
                          firstDelta.countDown();
                        }

                        public void onComplete(String text, List<ActorTaskReference> references) {}

                        public void onError(RuntimeException failure) {
                          throw failure;
                        }
                      }));
      assertThat(firstDelta.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(future.isDone()).isFalse();
      save("openai-compatible", "second-model", server.baseUrl() + "/v1");
      assertThat(future.get(10, TimeUnit.SECONDS).text()).isEqualTo("Old stream completes.");
      assertThat(turn("Use the new model")).isEqualTo("New provider request.");
      server.verify(
          postRequestedFor(urlEqualTo("/v1/chat/completions"))
              .withRequestBody(matchingJsonPath("$.model", equalTo("second-model"))));
    } finally {
      server.stop();
    }
  }

  @Test
  void providerToolSupportChangesWithTheClientAndResetsOnTheNextProvider() throws Exception {
    WireMockServer server = new WireMockServer(0);
    server.start();
    try {
      server.stubFor(
          WireMock.post(urlEqualTo("/v1/chat/completions"))
              .willReturn(
                  okJson(
                      openai(
                          new ObjectMapper()
                              .writeValueAsString(
                                  new TaskAgent.Result(
                                      Task.Status.completed, "Native task model response"))))));
      save("perplexity", "chat-model", server.baseUrl() + "/v1");
      assertThat(agentModel.task("fixture-task", "a task", List.of()).feedback())
          .contains("does not support tool execution");
      save("openai-compatible", "tool-model", server.baseUrl() + "/v1");
      assertThat(agentModel.task("fixture-task", "a task", List.of()).feedback())
          .isEqualTo("Native task model response");
    } finally {
      server.stop();
    }
  }

  private void save(String provider, String model, String endpoint) throws Exception {
    mvc.perform(
            post("/settings/provider")
                .param("provider", provider)
                .param("model", model)
                .param("baseUrl", endpoint)
                .param("apiKey", "fixture-secret-key"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("settingsMessage", containsString("applied")));
  }

  private String turn(String question) {
    var account =
        accounts.create(
            "live-provider-member-" + UUID.randomUUID(),
            "LiveProviderPassword-123",
            AccountRole.MEMBER);
    accounts.changePassword(
        account.id(), "LiveProviderPassword-123", "ChangedLiveProviderPassword-123");
    Actor actor = new Actor(account.id());
    return chat.chat(actor, chat.createWebConversation(actor), question).text();
  }

  private static String openai(String text) {
    return new ObjectMapper()
        .writeValueAsString(
            Map.of(
                "id",
                "fixture",
                "object",
                "chat.completion",
                "created",
                1,
                "model",
                "fixture-model",
                "choices",
                List.of(
                    Map.of(
                        "index",
                        0,
                        "message",
                        Map.of("role", "assistant", "content", text),
                        "finish_reason",
                        "stop"))));
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("zalava-live-provider-");
      Files.writeString(root.resolve("AGENT.md"), "Live provider component workspace.");
      return root;
    } catch (IOException failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }
}
