package org.zalava.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkCuratedMavenArtifactResolverBehaviorTest {

  private static final SourceModuleIndex.Artifact ARTIFACT =
      new SourceModuleIndex.Artifact("ai.sea.modules", "sea-module-fixture", "1.0.0");

  @TempDir Path workspace;

  @Test
  void rejectsHttpRepositoryBeforeNetworkAccess() {
    JdkCuratedMavenArtifactResolver resolver = new JdkCuratedMavenArtifactResolver(workspace);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("http://localhost/repository"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Curated Maven repository must use HTTPS");
  }

  @Test
  void resolvesArtifactWithChecksumAndCredentials() throws Exception {
    byte[] bytes = "fixture".getBytes();
    String digest;
    try {
      digest =
          "sha256:"
              + java.util.HexFormat.of()
                  .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
    HttpClient client = new StubHttpClient(bytes, digest);
    JdkCuratedMavenArtifactResolver resolver =
        new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    var result =
        resolver.resolve(new Request("curated", URI.create("https://repo.example/"), ARTIFACT));
    assertThat(Files.readAllBytes(Path.of(result.path()))).isEqualTo(bytes);
    assertThat(result.sha256Digest()).isEqualTo(digest);
  }

  @Test
  void rejectsChecksumMismatchAndDeletesTemporaryFile() {
    HttpClient client = new StubHttpClient("fixture".getBytes(), "sha256:" + "0".repeat(64));
    JdkCuratedMavenArtifactResolver resolver =
        new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void rejectsChecksumHttpFailure() {
    var resolver =
        new JdkCuratedMavenArtifactResolver(new StatusHttpClient(404), workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("checksum request failed");
  }

  @Test
  void rejectsArtifactHttpFailure() {
    var resolver =
        new JdkCuratedMavenArtifactResolver(new ArtifactFailureClient(), workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("artifact request failed");
  }

  @Test
  void rejectsMalformedChecksum() {
    var resolver =
        new JdkCuratedMavenArtifactResolver(new TextHttpClient("nope"), workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("checksum must be a SHA-256 digest");
  }

  @Test
  void rejectsMissingRequestArtifact() {
    JdkCuratedMavenArtifactResolver resolver = new JdkCuratedMavenArtifactResolver(workspace);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://localhost/repository"), null)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Curated Maven artifact request is required");
  }

  @Test
  void rejectsInvalidCoordinates() {
    var resolver = new JdkCuratedMavenArtifactResolver(workspace);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request(
                        "curated",
                        URI.create("https://repo.example"),
                        new SourceModuleIndex.Artifact("ai..sea", "fixture", "1.0.0"))))
        .isInstanceOf(SourceModuleInstallationException.class);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request(
                        "curated",
                        URI.create("https://repo.example"),
                        new SourceModuleIndex.Artifact("ai.sea", "bad id", "1.0.0"))))
        .isInstanceOf(SourceModuleInstallationException.class);
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request(
                        "curated",
                        URI.create("https://repo.example"),
                        new SourceModuleIndex.Artifact("ai.sea", "fixture", ".."))))
        .isInstanceOf(SourceModuleInstallationException.class);
  }

  @Test
  void credentialsValidateAndEncode() {
    var credentials = new JdkCuratedMavenArtifactResolver.Credentials("octocat", "package-token");
    assertThat(credentials.basicAuthorization()).isEqualTo("Basic b2N0b2NhdDpwYWNrYWdlLXRva2Vu");
    assertThatThrownBy(() -> new JdkCuratedMavenArtifactResolver.Credentials("", "token"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static class StatusHttpClient extends StubHttpClient {
    private final int status;

    StatusHttpClient(int status) {
      super(new byte[0], "0".repeat(64));
      this.status = status;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      return new Response<>(request, status, null);
    }
  }

  private static final class ArtifactFailureClient extends StubHttpClient {
    ArtifactFailureClient() {
      super(new byte[0], "0".repeat(64));
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      if (request.uri().getPath().endsWith(".sha256")) {
        @SuppressWarnings("unchecked")
        T body = (T) "0".repeat(64);
        return new Response<>(request, 200, body);
      }
      return new Response<>(request, 500, null);
    }
  }

  private static class TextHttpClient extends StubHttpClient {
    private final String text;

    TextHttpClient(String text) {
      super(new byte[0], text);
      this.text = text;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      @SuppressWarnings("unchecked")
      T body = (T) text;
      return new Response<>(request, 200, body);
    }
  }

  private static class StubHttpClient extends HttpClient {
    private final byte[] artifact;
    private final String checksum;

    StubHttpClient(byte[] artifact, String checksum) {
      this.artifact = artifact;
      this.checksum = checksum;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      boolean checksumRequest = request.uri().getPath().endsWith(".sha256");
      Object body =
          checksumRequest
              ? checksum.substring("sha256:".length())
              : new java.io.ByteArrayInputStream(artifact);
      @SuppressWarnings("unchecked")
      T converted = (T) body;
      return new Response<>(request, 200, converted);
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

    @Override
    public Optional<java.net.CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<java.time.Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public HttpClient.Redirect followRedirects() {
      return HttpClient.Redirect.NEVER;
    }

    @Override
    public Optional<java.net.ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public javax.net.ssl.SSLContext sslContext() {
      try {
        return javax.net.ssl.SSLContext.getDefault();
      } catch (java.security.NoSuchAlgorithmException exception) {
        throw new AssertionError(exception);
      }
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
  }

  private record Response<T>(HttpRequest request, int code, T value) implements HttpResponse<T> {
    public T body() {
      return value;
    }

    public int statusCode() {
      return code;
    }

    public HttpRequest request() {
      return request;
    }

    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    public URI uri() {
      return request.uri();
    }

    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    public Optional<javax.net.ssl.SSLSession> sslSession() {
      return Optional.empty();
    }
  }
}
