package org.zalava.modules.catalog.install.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;

class DefaultBinaryModuleInstallationTest {

  private BinaryArtifactInstallation artifacts;
  private ModuleEnablement enablement;
  private DefaultBinaryModuleInstallation service;

  @BeforeEach
  void setUp() {
    artifacts = mock(BinaryArtifactInstallation.class);
    enablement = mock(ModuleEnablement.class);
    when(artifacts.install(any(BinaryArtifactInstallation.Install.class)))
        .thenReturn(
            new BinaryArtifactInstallation.InstalledArtifact(
                "/mod/test-1.0.0.jar",
                "sha256:abababababababababababababababababababababababababababababababab"));
    when(enablement.enable(any()))
        .thenReturn(new ModuleEnablement.EnablementResult("/enabled.json"));
    service = new DefaultBinaryModuleInstallation(artifacts, enablement);
  }

  private static SourceModuleIndex.Module validModule(SourceModuleIndex.Source source) {
    return new SourceModuleIndex.Module(
        "test-module",
        "1.0.0",
        "Test",
        "desc",
        URI.create("https://example.com"),
        new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
        source,
        new SourceModuleIndex.Build(List.of("echo"), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        Map.of(),
        List.of(),
        List.of(),
        new SourceModuleIndex.Security(List.of("read")));
  }

  private static SourceModuleIndex.Source httpsSource() {
    return new SourceModuleIndex.Source(URI.create("https://github.com/x"), "MIT");
  }

  @Test
  void installValidModule() {
    var request =
        new BinaryModuleInstallRequest(
            validModule(httpsSource()),
            "/tmp/a.jar",
            "sha256:abababababababababababababababababababababababababababababababab",
            "maven-central");
    var result = service.install(request);
    assertThat(result.moduleId()).isEqualTo("test-module");
    assertThat(result.version()).isEqualTo("1.0.0");
  }

  @Test
  void installValidLocalPrivateModule() {
    var request =
        new BinaryModuleInstallRequest(
            validModule(null),
            "/tmp/a.jar",
            "sha256:abababababababababababababababababababababababababababababababab",
            "local-private");
    var result = service.install(request);
    assertThat(result.moduleId()).isEqualTo("test-module");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> service.install(null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  @Test
  void rejectsNullModule() {
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        null,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("metadata is required");
  }

  @Test
  void rejectsBlankModuleId() {
    var module =
        new SourceModuleIndex.Module(
            "",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("module id is required");
  }

  @Test
  void rejectsNullArtifact() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            null,
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("artifact coordinates are required");
  }

  @Test
  void rejectsBlankGroupId() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("group id is required");
  }

  @Test
  void rejectsNullSourceOutsideLocalPrivate() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            null,
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "maven-central")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("source metadata is required");
  }

  @Test
  void rejectsHttpSource() {
    var source = new SourceModuleIndex.Source(URI.create("http://github.com/x"), "MIT");
    var module = validModule(source);
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("must use HTTPS");
  }

  @Test
  void rejectsBlankLicense() {
    var source = new SourceModuleIndex.Source(URI.create("https://github.com/x"), "  ");
    var module = validModule(source);
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("license is required");
  }

  @Test
  void rejectsNullCompatibility() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            null,
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("compatibility is required");
  }

  @Test
  void rejectsNullSecurity() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            null);
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("permissions are required");
  }

  @Test
  void rejectsNullArtifactPath() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        null,
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("artifact path is required");
  }

  @Test
  void rejectsInvalidDigest() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(module, "/tmp/a.jar", "not-a-digest", "repo")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("SHA-256 digest");
  }

  @Test
  void rejectsBlankRepositoryId() {
    var module =
        new SourceModuleIndex.Module(
            "m",
            "1.0.0",
            "Test",
            "desc",
            URI.create("https://example.com"),
            new SourceModuleIndex.Artifact("g", "a", "1.0.0"),
            httpsSource(),
            new SourceModuleIndex.Build(List.of(), List.of()),
            new SourceModuleIndex.Compatibility(">=1.0.0"),
            Map.of(),
            List.of(),
            List.of(),
            new SourceModuleIndex.Security(List.of()));
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        module,
                        "/tmp/a.jar",
                        "sha256:abababababababababababababababababababababababababababababababab",
                        "  ")))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("repository id is required");
  }
}
