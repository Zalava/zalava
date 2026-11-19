package org.zalava.modules.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.ModuleLocatorIndexLoader;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
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
  void prefersPublishedIndexAndPinsItsCommit() {
    String publishedSha = "b".repeat(40);
    String ref =
        """
        {"ref":"refs/heads/published-release-index","object":{"type":"commit","sha":"%s"}}
        """
            .formatted(publishedSha);
    var locator = locatorWithIndexRef(200, ref);
    assertThat(locator.resolve("fixture").manifestUri())
        .isEqualTo(
            URI.create(
                "https://raw.githubusercontent.com/Zalava/zalava-module-fixture/"
                    + publishedSha
                    + "/releases/index.yaml"));
  }

  @Test
  void rejectsMalformedPublishedIndexRefWithoutFallingBack() {
    assertThatThrownBy(() -> locatorWithIndexRef(200, "{}").resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("did not return a module release-index revision");
  }

  @Test
  void propagatesPublishedIndexRefErrorsWithoutFallingBack() {
    assertThatThrownBy(() -> locatorWithIndexRef(403, "{}").resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("HTTP 403");
  }

  private JdkModuleLocatorReleaseLocator locatorWithIndexRef(int status, String body) {
    var client =
        new FixedClient(
            CATALOG.getBytes(),
            ("[{\"sha\":\"" + SHA + "\"}]").getBytes(),
            status,
            body.getBytes());
    return new JdkModuleLocatorReleaseLocator(
        client,
        URI.create("https://catalog.example"),
        new ModuleLocatorIndexLoader(),
        new ObjectMapper(),
        null);
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
    private final int indexRefStatus;
    private final byte[] indexRef;

    FixedClient(byte[] catalog, byte[] commits) {
      this(catalog, commits, 404, new byte[0]);
    }

    FixedClient(byte[] catalog, byte[] commits, int indexRefStatus, byte[] indexRef) {
      this.catalog = catalog;
      this.commits = commits;
      this.indexRefStatus = indexRefStatus;
      this.indexRef = indexRef;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      boolean isIndexRef =
          request.uri().getPath().endsWith("/git/ref/heads/published-release-index");
      byte[] payload = request.uri().getHost().equals("api.github.com") ? commits : catalog;
      @SuppressWarnings("unchecked")
      T body = (T) (isIndexRef ? indexRef : payload);
      return new Response<>(request, body, isIndexRef ? indexRefStatus : 200);
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

  private record Response<T>(HttpRequest request, T body, int statusCode)
      implements HttpResponse<T> {
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
