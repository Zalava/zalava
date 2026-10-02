package org.zalava.modules.catalog.install.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.BinaryModuleInstallRequest;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;

/**
 * Covers the MOD-BUNDLE-01 routing logic: manifest bundles versus multi-jar bundles versus the
 * single-artifact fast path, plus the discard-on-enablement-failure compensation.
 */
class DefaultBinaryModuleInstallationBundleRoutingTest {

  private static final String DIGEST =
      "sha256:abababababababababababababababababababababababababababababababab";

  private BinaryArtifactInstallation artifacts;
  private ModuleEnablement enablement;
  private DefaultBinaryModuleInstallation service;

  @BeforeEach
  void setUp() {
    artifacts = mock(BinaryArtifactInstallation.class);
    enablement = mock(ModuleEnablement.class);
    service = new DefaultBinaryModuleInstallation(artifacts, enablement);
    when(artifacts.install(any(BinaryArtifactInstallation.Install.class)))
        .thenReturn(new BinaryArtifactInstallation.InstalledArtifact("/mod/a-1.0.0.jar", DIGEST));
    when(enablement.enable(any()))
        .thenReturn(new ModuleEnablement.EnablementResult("/enabled.json"));
  }

  @Test
  void aManifestBundleRoutesThroughInstallBundle() {
    when(artifacts.installBundle(any(BinaryArtifactInstallation.Install.class)))
        .thenReturn(
            new BinaryArtifactInstallation.InstalledBundle(
                List.of(
                    new BinaryArtifactInstallation.InstalledArtifact("/mod/m.jar", DIGEST),
                    new BinaryArtifactInstallation.InstalledArtifact("/mod/lib/r.jar", DIGEST))));

    var result =
        service.install(
            new BinaryModuleInstallRequest(
                validModule(httpsSource()),
                "/tmp/a.jar",
                DIGEST,
                "maven-central",
                true,
                List.of()));

    assertThat(result.artifactPath()).isEqualTo("/mod/m.jar");
    ArgumentCaptor<BinaryArtifactInstallation.Install> bundle =
        ArgumentCaptor.forClass(BinaryArtifactInstallation.Install.class);
    verify(artifacts).installBundle(bundle.capture());
    assertThat(bundle.getValue().moduleId()).isEqualTo("test-module");
    verify(artifacts, never()).install(any(BinaryArtifactInstallation.Install.class));
    ArgumentCaptor<ModuleEnablement.EnabledModule> enabled =
        ArgumentCaptor.forClass(ModuleEnablement.EnabledModule.class);
    verify(enablement).enable(enabled.capture());
    assertThat(enabled.getValue().runtimeArtifacts()).hasSize(1);
    assertThat(enabled.getValue().runtimeArtifacts().getFirst().artifactPath())
        .isEqualTo("/mod/lib/r.jar");
  }

  @Test
  void multiJarBundlesRouteThroughTheStagedBundleInstall() {
    when(artifacts.install(any(BinaryArtifactInstallation.BundleInstall.class)))
        .thenReturn(
            new BinaryArtifactInstallation.InstalledBundle(
                List.of(
                    new BinaryArtifactInstallation.InstalledArtifact("/mod/a-1.0.0.jar", DIGEST),
                    new BinaryArtifactInstallation.InstalledArtifact("/mod/b-1.0.0.jar", DIGEST))));

    var result =
        service.install(
            new BinaryModuleInstallRequest(
                validModule(httpsSource()),
                "/tmp/a.jar",
                DIGEST,
                "maven-central",
                false,
                List.of(
                    new BinaryModuleInstallRequest.RuntimeArtifact(
                        new SourceModuleIndex.Artifact("g", "b", "1.0.0"), "/tmp/b.jar", DIGEST))));

    assertThat(result.artifactPath()).isEqualTo("/mod/a-1.0.0.jar");
    ArgumentCaptor<BinaryArtifactInstallation.BundleInstall> bundle =
        ArgumentCaptor.forClass(BinaryArtifactInstallation.BundleInstall.class);
    verify(artifacts).install(bundle.capture());
    assertThat(bundle.getValue().artifacts()).hasSize(2);
    verify(artifacts, never()).installBundle(any(BinaryArtifactInstallation.Install.class));
  }

  @Test
  void aSingleArtifactWithoutRuntimesKeepsUsingTheSimpleInstall() {
    var result =
        service.install(
            new BinaryModuleInstallRequest(
                validModule(httpsSource()), "/tmp/a.jar", DIGEST, "maven-central"));

    assertThat(result.artifactPath()).isEqualTo("/mod/a-1.0.0.jar");
    verify(artifacts).install(any(BinaryArtifactInstallation.Install.class));
    verify(artifacts, never()).install(any(BinaryArtifactInstallation.BundleInstall.class));
    verify(artifacts, never()).installBundle(any(BinaryArtifactInstallation.Install.class));
  }

  @Test
  void enablementFailureDiscardsStagedBundlesButNotSingleArtifacts() {
    when(enablement.enable(any())).thenThrow(new SourceModuleInstallationException("boom"));
    when(artifacts.install(any(BinaryArtifactInstallation.BundleInstall.class)))
        .thenReturn(
            new BinaryArtifactInstallation.InstalledBundle(
                List.of(new BinaryArtifactInstallation.InstalledArtifact("/mod/a.jar", DIGEST))));

    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        validModule(httpsSource()),
                        "/tmp/a.jar",
                        DIGEST,
                        "maven-central",
                        false,
                        List.of(
                            new BinaryModuleInstallRequest.RuntimeArtifact(
                                new SourceModuleIndex.Artifact("g", "b", "1.0.0"),
                                "/tmp/b.jar",
                                DIGEST)))))
        .isInstanceOf(SourceModuleInstallationException.class);
    verify(artifacts).discard(any(BinaryArtifactInstallation.InstalledBundle.class));

    org.mockito.Mockito.clearInvocations(artifacts);
    when(artifacts.install(any(BinaryArtifactInstallation.Install.class)))
        .thenReturn(new BinaryArtifactInstallation.InstalledArtifact("/mod/a.jar", DIGEST));

    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        validModule(httpsSource()), "/tmp/a.jar", DIGEST, "maven-central")))
        .isInstanceOf(SourceModuleInstallationException.class);
    verify(artifacts, never()).discard(any(BinaryArtifactInstallation.InstalledBundle.class));
  }

  @Test
  void localPrivateModulesMayOmitSourceMetadata() {
    var module = validModule(null);

    var result =
        service.install(
            new BinaryModuleInstallRequest(module, "/tmp/a.jar", DIGEST, "local-private"));

    assertThat(result.artifactPath()).isEqualTo("/mod/a-1.0.0.jar");
    ArgumentCaptor<ModuleEnablement.EnabledModule> enabled =
        ArgumentCaptor.forClass(ModuleEnablement.EnabledModule.class);
    verify(enablement).enable(enabled.capture());
    assertThat(enabled.getValue().sourceRepository()).isNull();
    assertThat(enabled.getValue().sourceLicense()).isNull();
  }

  @Test
  void runtimeArtifactValidationRejectsInvalidRuntimeMetadata() {
    assertThatThrownBy(
            () ->
                service.install(
                    new BinaryModuleInstallRequest(
                        validModule(httpsSource()),
                        "/tmp/a.jar",
                        DIGEST,
                        "maven-central",
                        false,
                        List.of(
                            new BinaryModuleInstallRequest.RuntimeArtifact(
                                new SourceModuleIndex.Artifact("g", "b", "1.0.0"),
                                "/tmp/b.jar",
                                "sha256:short")))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Binary module runtime artifact digest must be a SHA-256 digest");
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
}
