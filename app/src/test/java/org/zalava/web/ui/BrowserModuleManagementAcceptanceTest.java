package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.modules.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.modules.catalog.install.application.port.out.CuratedMavenArtifactResolver;
import org.zalava.modules.catalog.install.application.port.out.ModuleLocatorReleaseLocator;

/**
 * Real-browser acceptance for the product module marketplace: an administrator refreshes the
 * catalog, requests an installation, approves it, and sees the module enabled and persisted, all
 * through the real server-rendered product shell and the real install/enable path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@Import({
  BrowserModuleManagementAcceptanceTest.CatalogTestConfiguration.class,
  BrowserModuleManagementAcceptanceTest.AccountConfiguration.class,
  BrowserModuleManagementAcceptanceTest.LocalDevelopmentProjectInstallationTestConfiguration.class
})
class BrowserModuleManagementAcceptanceTest {
  private static final Path WORKSPACE = workspace();
  private static final String LOGIN = "module-management-" + UUID.randomUUID();
  private static final String PASSWORD = "ModuleManagementPassword-123";
  private static final AtomicReference<String> CURRENT_PASSWORD = new AtomicReference<>(PASSWORD);
  private static final String LOCAL_MODULE_LOGIN = "local-module-management-" + UUID.randomUUID();
  private static final String LOCAL_MODULE_PASSWORD = "LocalModuleManagementPassword-123";
  private static final AtomicReference<LocalDevelopmentProjectInstallation.Request>
      LOCAL_DEVELOPMENT_REQUEST = new AtomicReference<>();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.managed-restart.dispatcher-enabled", () -> "true");
    registry.add(
        "sea.module-configuration.root",
        () -> WORKSPACE.resolve("module-configuration").toString());
    registry.add("agent.modules.local-artifact-roots", () -> WORKSPACE.toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> LOGIN);
    registry.add("sea.accounts.bootstrap-password", () -> PASSWORD);
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void refreshesCatalogAndOpensModuleDetails() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);

      page.navigate(baseUrl() + "/modules");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Check catalog"))
          .waitFor();
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Check catalog")).click();
      page.locator("[data-catalog-module=zalava-module-tika]").waitFor();

      page.locator("[data-catalog-module=zalava-module-tika] [data-catalog-detail-link]").click();
      page.locator("[data-release-version]").waitFor();
      assertThat(page.locator("body").innerText()).contains("Catalog releases");
    }
  }

  @Test
  void preparesLocalDevelopmentInstallationThroughModulesPage() throws IOException {
    LOCAL_DEVELOPMENT_REQUEST.set(null);
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signInForLocalModuleInstallation(page);
      page.navigate(baseUrl() + "/modules");

      assertThat(page.url()).isEqualTo(baseUrl() + "/modules");

      page.getByRole(
              AriaRole.HEADING,
              new Page.GetByRoleOptions().setName("Install a local build").setExact(true))
          .waitFor();
      page.locator("input[name=projectDirectory]").fill("/mounted/modules/zalava-module-example");
      page.locator("input[name=moduleId]").fill("zalava-module-example");
      page.locator("input[name=version]").fill("1.2.3");
      page.locator("input[name=developmentRequestId]").fill("development-42");
      page.locator("[data-action=prepare-local-module]").click();

      page.getByText(
              "Local module zalava-module-example 1.2.3 installed. Restart SEA once to load its classes.")
          .waitFor();
    }

    assertThat(LOCAL_DEVELOPMENT_REQUEST.get())
        .isEqualTo(
            new LocalDevelopmentProjectInstallation.Request(
                "/mounted/modules/zalava-module-example",
                "zalava-module-example",
                "1.2.3",
                "development-42"));
  }

  @Test
  void exposesUploadedModuleInstallationThroughModulesPage() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);
      page.navigate(baseUrl() + "/modules");
      page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Upload a module JAR"))
          .waitFor();
      page.locator("input[name=moduleJar][type=file]")
          .setInputFiles(
              new com.microsoft.playwright.options.FilePayload(
                  "fixture.jar", "application/java-archive", uploadedJar()));
      page.locator("[data-action=upload-module]").click();
      page.getByText("Uploaded uploaded-fixture 1.0.0 installed").waitFor();
    }
  }

  @Test
  void requestsManagedSeaRestartThroughModulesPage() throws IOException {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext()) {
      Page page = context.newPage();
      signIn(page);
      page.navigate(baseUrl() + "/modules");

      page.locator("[data-action=restart-sea]").click();

      page.locator("[data-sea-restart-status=REQUESTED]").waitFor();
      assertThat(page.locator("[data-sea-restart-status]").getAttribute("data-sea-restart-status"))
          .isEqualTo("REQUESTED");
      assertThat(page.locator("[data-action=restart-sea]").isDisabled()).isTrue();
    }
  }

  private static byte[] uploadedJar() throws IOException {
    try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(bytes)) {
      jar.putNextEntry(new java.util.jar.JarEntry("module-metadata.yaml"));
      jar.write(
          """
          schemaVersion: 1
          modules:
            - moduleId: uploaded-fixture
              version: 1.0.0
              displayName: Uploaded fixture
              description: Fixture
              supportUrl: https://example.test/uploaded-fixture
              artifact:
                groupId: org.zalava
                artifactId: uploaded-fixture
                version: 1.0.0
              compatibility:
                seaRuntime: ">=1.0.0"
              configurationSchema:
                type: object
              factories:
                - factoryId: fixture
                  providerType: fixture
              operations:
                - name: fixtureOperation
                  description: Fixture operation
                  sideEffecting: true
                  inputSchema:
                    type: object
              security:
                permissions: [file.read]
          """
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      jar.closeEntry();
      jar.finish();
      return bytes.toByteArray();
    }
  }

  private void signIn(Page page) {
    String password = CURRENT_PASSWORD.get();
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(LOGIN);
    page.locator("input[name=password]").fill(password);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    if (!page.url().endsWith("/account/password")) {
      return;
    }
    String replacement = password + "-changed";
    page.locator("input[name=currentPassword]").fill(password);
    page.locator("input[name=replacementPassword]").fill(replacement);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password")).click();
    CURRENT_PASSWORD.compareAndSet(password, replacement);
  }

  private void signInForLocalModuleInstallation(Page page) {
    page.navigate(baseUrl() + "/login");
    page.locator("input[name=username]").fill(LOCAL_MODULE_LOGIN);
    page.locator("input[name=password]").fill(LOCAL_MODULE_PASSWORD);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.locator("input[name=currentPassword]").waitFor();
    page.locator("input[name=currentPassword]").fill(LOCAL_MODULE_PASSWORD);
    page.locator("input[name=replacementPassword]").fill(LOCAL_MODULE_PASSWORD + "-changed");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password")).click();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("sea-browser-module-management-");
      Files.writeString(root.resolve("AGENT.md"), "Browser module management workspace.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AccountConfiguration {
    @Bean
    @Order(-100)
    ApplicationRunner browserModuleManagementAccount(AccountLifecycle accounts) {
      return arguments -> {
        accounts
            .findByLoginName(LOGIN)
            .orElseGet(() -> accounts.create(LOGIN, PASSWORD, AccountRole.ADMIN));
        accounts
            .findByLoginName(LOCAL_MODULE_LOGIN)
            .orElseGet(
                () ->
                    accounts.create(LOCAL_MODULE_LOGIN, LOCAL_MODULE_PASSWORD, AccountRole.ADMIN));
      };
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class LocalDevelopmentProjectInstallationTestConfiguration {
    @Bean
    @Primary
    LocalDevelopmentProjectInstallation browserLocalDevelopmentProjectInstallation() {
      return request -> {
        LOCAL_DEVELOPMENT_REQUEST.set(request);
        return null;
      };
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CatalogTestConfiguration {

    @Bean
    @Primary
    ModuleLocatorReleaseLocator deterministicModuleLocator() {
      return new ModuleLocatorReleaseLocator() {
        @Override
        public List<Module> modules() {
          return List.of(new Module("zalava-module-tika", "Tika", "Content extraction"));
        }

        @Override
        public ResolvedModule resolve(String moduleId) {
          return new ResolvedModule(
              moduleId,
              moduleId,
              "description",
              URI.create("https://example.test/" + moduleId + "/releases/index.yaml"),
              URI.create("https://example.test/maven/" + moduleId));
        }
      };
    }

    @Bean
    @Primary
    ModuleReleaseIndexRetrieval deterministicReleaseIndexes() {
      return (uri, token) -> {
        String moduleId = uri.getPath().split("/")[1];
        return new ModuleReleaseIndex(
            1, moduleId, List.of(release("1.0.1", "v1.0.1"), release("1.1.0", "v1.1.0")));
      };
    }

    @Bean
    @Primary
    CuratedMavenArtifactResolver deterministicArtifacts() {
      return new CuratedMavenArtifactResolver() {
        @Override
        public ResolvedArtifact resolve(Request request) {
          try {
            Path jar = Files.createTempFile("zalava-module-release-", ".jar");
            Files.writeString(jar, "fixture module artifact");
            return new ResolvedArtifact(jar.toString(), "sha256:" + fixtureDigest());
          } catch (IOException ex) {
            throw new IllegalStateException("Unable to stage fixture artifact", ex);
          }
        }

        @Override
        public void discard(ResolvedArtifact artifact) {}
      };
    }

    private static String fixtureDigest() {
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        "fixture module artifact"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new IllegalStateException(ex);
      }
    }

    private static ModuleReleaseIndex.Release release(String version, String tag) {
      return new ModuleReleaseIndex.Release(
          version,
          tag,
          new ModuleReleaseIndex.Artifact("org.example", "module", version, fixtureDigest()),
          new ModuleReleaseIndex.Source(
              URI.create("https://github.com/example/module"), "Apache-2.0"),
          new ModuleReleaseIndex.Compatibility(">=1.0.0 <2.0.0"),
          new ModuleReleaseIndex.Security(List.of()));
    }
  }
}
