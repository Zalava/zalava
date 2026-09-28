package org.zalava.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.catalog.ModuleLocatorIndexLoader;
import org.zalava.catalog.install.SourceModuleInstallationException;
import tools.jackson.databind.ObjectMapper;

class JdkModuleLocatorReleaseLocatorTest {
  private static final String CATALOG =
      "schemaVersion: 1\nrepository:\n  type: module-locator\n  indexRepository: https://github.com/Zalava/zalava-catalog\n  indexPath: catalog.yaml\nmodules:\n  - moduleId: fixture\n    displayName: Fixture\n    description: Fixture module\n    repository: https://github.com/Zalava/zalava-module-fixture\n    releaseIndexPath: releases/index.yaml\n";
  private static final String SHA = "a".repeat(40);

  @Test
  void resolvesPinnedRawManifestUri() {
    var client = new FixedClient(CATALOG.getBytes(), ("[{\"sha\":\"" + SHA + "\"}]").getBytes());
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            "token");
    var result = locator.resolve("fixture");
    assertThat(result.moduleId()).isEqualTo("fixture");
    assertThat(result.manifestUri().toString()).contains(SHA).contains("releases/index.yaml");
  }

  @Test
  void rejectsUnknownModule() {
    var locator =
        new JdkModuleLocatorReleaseLocator(
            new FixedClient(CATALOG.getBytes(), "[]".getBytes()),
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("missing"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("not present");
  }

  @Test
  void rejectsInvalidCatalogUrl() {
    assertThatThrownBy(
            () -> new JdkModuleLocatorReleaseLocator(URI.create("http://catalog.example")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class FixedClient extends HttpClient {
    private final byte[] catalog;
    private final byte[] commits;

    FixedClient(byte[] catalog, byte[] commits) {
      this.catalog = catalog;
      this.commits = commits;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      @SuppressWarnings("unchecked")
      T body = (T) (request.uri().getHost().equals("api.github.com") ? commits : catalog);
      return new Response<>(request, body);
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

  private record Response<T>(HttpRequest request, T body) implements HttpResponse<T> {
    public int statusCode() {
      return 200;
    }

    public HttpRequest request() {
      return request;
    }

    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    public java.net.http.HttpHeaders headers() {
      return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
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
