package org.zalava.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver.Request;

class JdkCuratedMavenArtifactResolverSendTest {

  private static final SourceModuleIndex.Artifact ARTIFACT =
      new SourceModuleIndex.Artifact("ai.sea.modules", "sea-module-fixture", "1.0.0");

  @TempDir Path workspace;

  @Test
  void sendThrowsOnIoException() {
    var client = new ExceptionClient(new IOException("network down"));
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Unable to request curated Maven repository");
  }

  @Test
  void sendThrowsOnInterrupted() {
    var client = new ExceptionClient(new InterruptedException("stopped"));
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("interrupted");
  }

  @Test
  void temporaryFileRejectsSymlink() {
    try {
      Path downloads = workspace.resolve("source-module-installation/downloads");
      Files.createDirectories(downloads);
      // Replace the directory with a symlink to trigger the guard
      Files.delete(downloads);
      Files.createSymbolicLink(downloads, workspace.resolve("target"));
      byte[] bytes = "fixture".getBytes();
      String digest = sha256(bytes);
      var client =
          new JdkCuratedMavenArtifactResolverExtendedTest.ArtifactClient(bytes, digest, 200);
      var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
      assertThatThrownBy(
              () ->
                  resolver.resolve(
                      new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
          .isInstanceOf(SourceModuleInstallationException.class)
          .hasMessageContaining("symbolic link");
    } catch (IOException e) {
      // Symlinks may not be supported; skip
    }
  }

  @Test
  void nullRepositoryUrlRejected() {
    var resolver = new JdkCuratedMavenArtifactResolver(workspace);
    assertThatThrownBy(() -> resolver.resolve(new Request("curated", null, ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("required");
  }

  private static String sha256(byte[] data) {
    try {
      return "sha256:"
          + java.util.HexFormat.of()
              .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  static class ExceptionClient extends HttpClient {
    private final Exception exception;

    ExceptionClient(Exception e) {
      this.exception = e;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (exception instanceof IOException io) throw io;
      if (exception instanceof InterruptedException ie) throw ie;
      throw new RuntimeException(exception);
    }

    @Override
    public Optional<java.net.CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<java.time.Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<java.net.ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public javax.net.ssl.SSLContext sslContext() {
      return null;
    }

    @Override
    public javax.net.ssl.SSLParameters sslParameters() {
      return new javax.net.ssl.SSLParameters();
    }

    @Override
    public Optional<java.net.Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Optional<java.util.concurrent.Executor> executor() {
      return Optional.empty();
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest r, HttpResponse.BodyHandler<T> h) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
      throw new UnsupportedOperationException();
    }
  }
}
