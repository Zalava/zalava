package org.zalava.knowledge.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.zalava.api.extensions.content.ContentExtractionFailureCategory;
import org.zalava.api.extensions.managed.ManagedOcrService;
import org.zalava.api.extensions.managed.ManagedServiceEngine;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeExtractionJob;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeDerivationStore;
import org.zalava.knowledge.application.port.out.KnowledgeExtractionRecordStore;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest.Status;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;
import org.zalava.modules.managedservices.application.port.in.ModuleManagedServiceInstallation;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.RestartableZalavaApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

/** Uses released bundles and the declared worker without an external OCR endpoint. */
@Tag("ocr-worker")
class ManagedOcrWorkerAcceptanceTest {
  private static final String DOCKER = "zalava-module-docker";
  private static final String TIKA = "zalava-module-tika";
  private static final String DOCKER_VERSION = "0.1.0-alpha.7";
  private static final String TIKA_VERSION = "0.1.0-alpha.6";
  private static final String WORKER = ManagedOcrService.SERVICE_ID;

  @TempDir Path workspace;

  @Test
  void approvesReleasedWorkerAndExtractsScansAcrossAHostRestart() throws Exception {
    String database = PostgreSqlTestDatabase.newJdbcUrl();
    try (var host = start(database)) {
      install(host, DOCKER, DOCKER_VERSION, "4b5e08f05810917b8b0707c69a1ab1481ac47314");
      install(host, TIKA, TIKA_VERSION, "37f99b715e5ca2685e136fc6b8cb7b70fcf02f52");
      var configurations = host.getBean(FileSystemModuleConfigurationStore.class);
      configurations.saveCandidate(
          new ModuleConfigurationSnapshot(
              DOCKER,
              DOCKER_VERSION,
              DOCKER + "-" + DOCKER_VERSION,
              Map.of("services", Map.of("engineEndpoint", "unix:///var/run/docker.sock")),
              Map.of()),
          Map.of());
      configurations.promoteCandidate(DOCKER);
      configurations.saveCandidate(
          new ModuleConfigurationSnapshot(
              TIKA,
              TIKA_VERSION,
              TIKA + "-" + TIKA_VERSION,
              Map.of("services", Map.of()),
              Map.of()),
          Map.of());
      configurations.promoteCandidate(TIKA);
    }

    String ownedIdentity = null;
    try {
      try (var host = start(database)) {
        var runtime = (ManagedZalavaRuntime) host.getBean(ZalavaRuntime.class);
        runtime.start(DOCKER);
        runtime.start(TIKA);
        var engine = engine(host);
        assertThat(engine.inspect(WORKER).exists()).as("disposable worker name is free").isFalse();
        installWorker(host);
        var status = engine.inspect(WORKER);
        if (status.exists() && TIKA.equals(status.ownerModuleId())) {
          ownedIdentity = status.dataIdentity();
        }
        assertThat(host.getBean(ModuleManagedServiceInstallation.class).latestForModule(TIKA))
            .hasValueSatisfying(
                result ->
                    assertThat(result.status())
                        .as(result.message())
                        .isEqualTo(ManagedServiceInstallRequest.Status.SUCCEEDED));
        assertThat(status.ready()).isTrue();
        assertThat(engine.isNetworkIsolated(WORKER)).isTrue();
        assertScan(host, "before-restart.png");
      }
      try (var host = start(database, true)) {
        assertThat(engine(host).inspect(WORKER).dataIdentity()).isEqualTo(ownedIdentity);
        var source = uploadAndCiteScan(host);
        engine(host).stop(WORKER);
        host.getBean(KnowledgeExtractionJob.class).extract(source);
        var records = host.getBean(KnowledgeExtractionRecordStore.class);
        assertThat(records.find(source, 2))
            .hasValueSatisfying(
                record ->
                    assertThat(record.failureCategory())
                        .isEqualTo(ContentExtractionFailureCategory.UNAVAILABLE));
        assertThat(host.getBean(KnowledgeDerivationStore.class).active(source))
            .hasValueSatisfying(derivation -> assertThat(derivation.version()).isEqualTo(1));
      }
    } finally {
      if (ownedIdentity != null) {
        try (var host = start(database)) {
          var engine = engine(host);
          var status = engine.inspect(WORKER);
          if (status.exists()
              && TIKA.equals(status.ownerModuleId())
              && ownedIdentity.equals(status.dataIdentity())) {
            engine.remove(WORKER);
          }
        }
      }
    }
  }

  private ConfigurableApplicationContext start(String database) {
    return start(database, false);
  }

  private ConfigurableApplicationContext start(String database, boolean browser) {
    return RestartableZalavaApplicationContext.start(
        workspace,
        Map.of(
            "spring.datasource.url", database,
            "zalava.module-configuration.root", workspace.toString(),
            "agent.onboarding.completed", "true",
            "agent.channels.telegram.token", "false",
            "agent.channels.telegram.username", "false",
            "spring.ai.model.chat", browser ? "openai" : "unknown",
            "spring.ai.openai.api-key", "local-scripted-ocr-model",
            "zalava.accounts.security-enabled", Boolean.toString(browser),
            "zalava.knowledge.maximum-text-characters", "1000000"),
        browser ? new Class<?>[] {OcrModelConfiguration.class} : new Class<?>[0]);
  }

  private static KnowledgeSourceId uploadAndCiteScan(ConfigurableApplicationContext host)
      throws Exception {
    String name = "after-restart.pdf";
    String base =
        "http://127.0.0.1:" + ((ServletWebServerApplicationContext) host).getWebServer().getPort();
    try (var playwright = Playwright.create();
        var browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        var context = browser.newContext()) {
      Page page = context.newPage();
      page.navigate(base + "/login");
      page.locator("input[name=username]").fill("knowledge-admin");
      page.locator("input[name=password]").fill("KnowledgePassword-123");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      if (page.url().endsWith("/account/password")) {
        page.locator("input[name=currentPassword]").fill("KnowledgePassword-123");
        page.locator("input[name=replacementPassword]").fill("KnowledgePassword-456");
        page.locator("input[name=passwordConfirmation]").fill("KnowledgePassword-456");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
            .click();
      }
      page.navigate(base + "/knowledge");
      page.locator("#knowledge-file")
          .setInputFiles(new FilePayload(name, "application/pdf", fixture("scanned-invoice.pdf")));
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload").setExact(true))
          .click();
      page.getByText("Source uploaded for processing.").waitFor();
      String href =
          page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true))
              .getAttribute("href");
      var sourceId = new KnowledgeSourceId(UUID.fromString(href.substring("/knowledge/".length())));
      host.getBean(KnowledgeExtractionJob.class).extract(sourceId);
      assertThat(host.getBean(KnowledgeExtractionRecordStore.class).find(sourceId, 1))
          .hasValueSatisfying(
              record -> assertThat(record.text()).contains("HOUSEHOLD INVOICE 89 EUROS"));

      page.navigate(base + "/chat");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New conversation"))
          .click();
      page.locator("#message").fill("What is the total on the scanned invoice I uploaded?");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
          .click();
      var citation =
          page.getByRole(
              AriaRole.LINK, new Page.GetByRoleOptions().setName("View scanned invoice"));
      citation.waitFor();
      assertThat(citation.getAttribute("href")).isEqualTo(href);
      assertThat(page.getByText("The scanned invoice total is 89 euros.").count()).isPositive();
      citation.click();
      page.waitForURL(base + href);
      page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setExact(true))
          .waitFor();
      return sourceId;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OcrModelConfiguration {
    @Bean
    @Primary
    ChatModel ocrTestModel() {
      return new ChatModel() {
        public ChatOptions getOptions() {
          return ToolCallingChatOptions.builder().build();
        }

        public ChatResponse call(Prompt prompt) {
          if (prompt.getInstructions().getLast() instanceof ToolResponseMessage toolResponse) {
            var result =
                new JsonMapper().readTree(toolResponse.getResponses().getFirst().responseData());
            for (var source : result.path("sources")) {
              if ("after-restart.pdf".equals(source.path("displayName").asString())
                  && source.path("excerpt").asString().contains("HOUSEHOLD INVOICE 89 EUROS")) {
                String answer =
                    "The scanned invoice total is 89 euros. [View scanned invoice]("
                        + source.path("citation").asString()
                        + ")";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
              }
            }
            throw new IllegalStateException(
                "Knowledge did not return the scanned invoice evidence");
          }
          var options = (ToolCallingChatOptions) prompt.getOptions();
          String name =
              options.getToolCallbacks().stream()
                  .map(callback -> callback.getToolDefinition().name())
                  .filter(candidate -> candidate.startsWith("zalava_knowledge_search_"))
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("Knowledge search tool is unavailable"));
          var message =
              AssistantMessage.builder()
                  .toolCalls(
                      List.of(
                          new AssistantMessage.ToolCall(
                              "ocr-search",
                              "function",
                              name,
                              "{\"query\":\"HOUSEHOLD INVOICE\",\"limit\":5}")))
                  .build();
          return new ChatResponse(List.of(new Generation(message)));
        }

        public Flux<ChatResponse> stream(Prompt prompt) {
          return Flux.defer(() -> Flux.just(call(prompt))).subscribeOn(Schedulers.boundedElastic());
        }
      };
    }
  }

  private static void install(
      ConfigurableApplicationContext host, String module, String version, String indexCommit) {
    var installation =
        host.getBean("rawModuleReleaseInstallation", ModuleReleaseInstallation.class);
    var index =
        URI.create(
            "https://raw.githubusercontent.com/Zalava/"
                + module
                + "/"
                + indexCommit
                + "/releases/index.yaml");
    var request =
        installation.create(
            new ModuleReleaseInstallation.Request(
                index,
                null,
                module,
                version,
                "github-packages",
                URI.create("https://github.com/Zalava/" + module),
                null));
    assertThat(installation.allow(request.requestId()).status()).isEqualTo(Status.SUCCEEDED);
  }

  private static void installWorker(ConfigurableApplicationContext host) throws Exception {
    int port = ((ServletWebServerApplicationContext) host).getWebServer().getPort();
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + port + "/modules/" + TIKA + "/managed-services/request"))
            .timeout(Duration.ofMinutes(3))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    try (var http = HttpClient.newHttpClient()) {
      var response = http.send(request, HttpResponse.BodyHandlers.discarding());
      assertThat(response.statusCode()).isEqualTo(302);
      assertThat(response.headers().firstValue("location"))
          .hasValueSatisfying(location -> assertThat(location).endsWith("/modules/" + TIKA));
    }
  }

  private static ManagedServiceEngine engine(ConfigurableApplicationContext host) {
    return host.getBean(ZalavaRuntime.class)
        .findService(ManagedServiceEngine.CONTRACT)
        .orElseThrow()
        .service();
  }

  private static KnowledgeSourceId assertScan(ConfigurableApplicationContext host, String name)
      throws Exception {
    var accounts = host.getBean(AccountLifecycle.class);
    var owner = new Actor(accounts.findByLoginName("knowledge-admin").orElseThrow().id());
    var source =
        host.getBean(KnowledgeSourceLifecycle.class)
            .register(owner, name, "image/png", fixture("scanned-invoice.png"));
    host.getBean(KnowledgeExtractionJob.class).extract(source.id());
    var extracted =
        host.getBean(KnowledgeExtractionRecordStore.class).find(source.id(), 1).orElseThrow();
    assertThat(extracted.text()).contains("HOUSEHOLD INVOICE 89 EUROS");
    return source.id();
  }

  private static byte[] fixture(String name) throws Exception {
    try (var input = ManagedOcrWorkerAcceptanceTest.class.getResourceAsStream("/ocr/" + name)) {
      return input.readAllBytes();
    }
  }
}
