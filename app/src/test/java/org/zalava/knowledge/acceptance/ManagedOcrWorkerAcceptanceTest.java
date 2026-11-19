package org.zalava.knowledge.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
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
      try (var host = start(database)) {
        assertThat(engine(host).inspect(WORKER).dataIdentity()).isEqualTo(ownedIdentity);
        var source = assertScan(host, "after-restart.png");
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
    return RestartableZalavaApplicationContext.start(
        workspace,
        Map.of(
            "spring.datasource.url", database,
            "zalava.module-configuration.root", workspace.toString(),
            "agent.onboarding.completed", "true",
            "agent.channels.telegram.token", "false",
            "agent.channels.telegram.username", "false",
            "spring.ai.model.chat", "unknown",
            "zalava.knowledge.maximum-text-characters", "1000000"));
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
    byte[] scan;
    try (var input =
        ManagedOcrWorkerAcceptanceTest.class.getResourceAsStream("/ocr/scanned-invoice.png")) {
      scan = input.readAllBytes();
    }
    var source =
        host.getBean(KnowledgeSourceLifecycle.class).register(owner, name, "image/png", scan);
    host.getBean(KnowledgeExtractionJob.class).extract(source.id());
    var extracted =
        host.getBean(KnowledgeExtractionRecordStore.class).find(source.id(), 1).orElseThrow();
    assertThat(extracted.text()).contains("HOUSEHOLD INVOICE 89 EUROS");
    return source.id();
  }
}
