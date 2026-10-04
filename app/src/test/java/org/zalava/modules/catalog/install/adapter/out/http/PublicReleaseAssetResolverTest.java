package org.zalava.modules.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.ModuleArtifactRepository;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.CuratedMavenArtifactResolver;

class PublicReleaseAssetResolverTest {
  @TempDir Path workspace;
  private static final ModuleArtifactRepository.GitHubReleaseAsset REPOSITORY =
      new ModuleArtifactRepository.GitHubReleaseAsset(
          "zalava-module-time",
          URI.create("https://github.com/Zalava/zalava-module-time"),
          "v0.1.0-alpha.4",
          "zalava-module-time-0.1.0-alpha.4.jar");

  @Test
  void downloadsDeclaredAssetWithoutMavenCredentials() throws Exception {
    byte[] bytes = "released-fixture".getBytes();
    HttpClient client = client(bytes);
    var resolver =
        new JdkCuratedMavenArtifactResolver(
            client,
            workspace,
            Map.of(
                "github-packages",
                new JdkCuratedMavenArtifactResolver.Credentials("user", "secret")));
    String digest =
        "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var artifact = resolver.resolve(request(digest));
    assertThat(Files.readAllBytes(Path.of(artifact.path()))).isEqualTo(bytes);
    assertThat(artifact.sha256Digest()).isEqualTo(digest);
    var captured = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(captured.capture(), any());
    assertThat(captured.getValue().uri().toString())
        .isEqualTo(
            "https://github.com/Zalava/zalava-module-time/releases/download/v0.1.0-alpha.4/zalava-module-time-0.1.0-alpha.4.jar");
    assertThat(captured.getValue().headers().firstValue("Authorization")).isEmpty();
    resolver.discard(artifact);
    assertThat(Path.of(artifact.path())).doesNotExist();
  }

  @Test
  void rejectsUnpinnedOrMismatchedBytesAndCleansDownloads() throws Exception {
    var resolver =
        new JdkCuratedMavenArtifactResolver(client("wrong".getBytes()), workspace, Map.of());
    assertThatThrownBy(() -> resolver.resolve(request(null)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("index-pinned SHA-256");
    assertThatThrownBy(() -> resolver.resolve(request("sha256:" + "0".repeat(64))))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match release index");
    try (var files = Files.list(workspace.resolve("source-module-installation/downloads"))) {
      assertThat(files).isEmpty();
    }
  }

  private static CuratedMavenArtifactResolver.Request request(String digest) {
    return new CuratedMavenArtifactResolver.Request(
        "github-packages",
        URI.create("https://maven.pkg.github.com/Zalava/zalava-module-time"),
        new SourceModuleIndex.Artifact("org.zalava.modules", "zalava-module-time", "0.1.0-alpha.4"),
        REPOSITORY,
        digest);
  }

  @SuppressWarnings("unchecked")
  private static HttpClient client(byte[] bytes) throws Exception {
    HttpClient client = mock(HttpClient.class);
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
    when(client.<InputStream>send(any(), any())).thenReturn(response);
    return client;
  }
}
