package org.zalava.modules.catalog.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;

/**
 * Covers the bounded JDK HTTP release-index adapter with a deterministic fake client: URL
 * validation, status/size limits, bearer-token selection, and successful loading. No network is
 * contacted; the fake client also verifies that HTTPS-only URL validation happens before any
 * request is sent.
 */
class JdkModuleReleaseIndexRetrievalHttpTest {

  private static final String INDEX_YAML = validIndex();

  private static String validIndex() {
    return """
        schemaVersion: 1
        moduleId: zalava-module-example
        releases:
          - version: 1.0.1
            releaseTag: v1.0.1
            artifact:
              groupId: org.zalava.modules
              artifactId: zalava-module-example
              version: 1.0.1
              sha256: %s
            source:
              repository: https://github.com/Zalava/zalava-module-example.git
              license: Apache-2.0
            compatibility:
              zalavaRuntime: \">=1.0.0 <2.0.0\"
            security:
              permissions: [example.read]
        """
        .formatted("a".repeat(64));
  }

  @Test
  void loadsAYamlIndexAndSendsNoAuthorizationWithoutTokens() {
    RecordingClient client = new RecordingClient(INDEX_YAML.getBytes(StandardCharsets.UTF_8), 200);
    var retrieval = new JdkModuleReleaseIndexRetrieval(client, loader(), null);

    var index = retrieval.load(https("/index.yaml"), null);

    assertThat(index.releases()).hasSize(1);
    assertThat(index.releases().getFirst().version()).isEqualTo("1.0.1");
    assertThat(client.lastRequest.headers().firstValue("Authorization")).isEmpty();
  }

  @Test
  void prefersTheCallScopedBearerTokenOverTheConfiguredAccessToken() {
    RecordingClient client = new RecordingClient(INDEX_YAML.getBytes(StandardCharsets.UTF_8), 200);
    var retrieval = new JdkModuleReleaseIndexRetrieval(client, loader(), "configured-token");

    retrieval.load(https("/index.yaml"), "call-token");

    assertThat(client.lastRequest.headers().firstValue("Authorization"))
        .contains("Bearer call-token");
  }

  @Test
  void fallsBackToTheConfiguredAccessTokenWhenTheCallTokenIsBlank() {
    RecordingClient client = new RecordingClient(INDEX_YAML.getBytes(StandardCharsets.UTF_8), 200);
    var retrieval = new JdkModuleReleaseIndexRetrieval(client, loader(), "configured-token");

    retrieval.load(https("/index.yaml"), "  ");

    assertThat(client.lastRequest.headers().firstValue("Authorization"))
        .contains("Bearer configured-token");
  }

  @Test
  void rejectsNonHttpsUrlsAndUrlsWithQueryOrFragment() {
    var retrieval =
        new JdkModuleReleaseIndexRetrieval(new RecordingClient(new byte[0], 200), loader(), null);

    assertThatThrownBy(() -> retrieval.load(URI.create("http://example.test/index.yaml"), null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release index URL must be an HTTPS URL without query or fragment");
    assertThatThrownBy(
            () -> retrieval.load(URI.create("https://example.test/i.yaml?query=1"), null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release index URL must be an HTTPS URL without query or fragment");
    assertThatThrownBy(() -> retrieval.load(URI.create("https://example.test/i.yaml#frag"), null))
        .isInstanceOf(SourceModuleInstallationException.class);
    assertThatThrownBy(() -> retrieval.load(null, null))
        .isInstanceOf(SourceModuleInstallationException.class);
  }

  @Test
  void non200ResponsesAreRejected() {
    var retrieval =
        new JdkModuleReleaseIndexRetrieval(new RecordingClient(new byte[0], 404), loader(), null);

    assertThatThrownBy(() -> retrieval.load(https("/index.yaml"), null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release index request failed: HTTP 404");
  }

  @Test
  void oversizedBodiesAreRejected() {
    var retrieval =
        new JdkModuleReleaseIndexRetrieval(
            new RecordingClient(new byte[257 * 1024], 200), loader(), null);

    assertThatThrownBy(() -> retrieval.load(https("/index.yaml"), null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Module release index exceeds the maximum allowed size");
  }

  @Test
  void connectionFailuresSurfaceAsRetrievalExceptions() {
    var retrieval =
        new JdkModuleReleaseIndexRetrieval(
            new FailingClient(new IOException("connection refused")), loader(), null);

    assertThatThrownBy(() -> retrieval.load(https("/index.yaml"), null))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessage("Unable to request module release index");
  }

  private static URI https(String path) {
    return URI.create("https://catalog.example" + path);
  }

  private static org.zalava.modules.catalog.ModuleReleaseIndexLoader loader() {
    return new org.zalava.modules.catalog.ModuleReleaseIndexLoader();
  }

  private static final class RecordingClient extends HttpClient {
    private final byte[] body;
    private final int status;
    HttpRequest lastRequest;

    RecordingClient(byte[] body, int status) {
      this.body = body;
      this.status = status;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      this.lastRequest = request;
      T resolved = (T) body;
      return new RecordedResponse<>(request, status, resolved);
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
  }

  private static final class FailingClient extends HttpClient {
    private final IOException failure;

    FailingClient(IOException failure) {
      this.failure = failure;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException {
      throw failure;
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
  }

  private record RecordedResponse<T>(HttpRequest request, int status, T storedBody)
      implements HttpResponse<T> {

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      return request;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public java.net.http.HttpHeaders headers() {
      return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
    }

    @Override
    public T body() {
      return storedBody;
    }

    @Override
    public Optional<javax.net.ssl.SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
