package org.zalava.modules.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.modules.catalog.ModuleLocatorIndexLoader;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import tools.jackson.databind.ObjectMapper;

class JdkModuleLocatorReleaseLocatorExtendedTest {
  private static final String CATALOG =
      "schemaVersion: 1\nrepository:\n  type: module-locator\n  indexRepository: https://github.com/Zalava/zalava-catalog\n  indexPath: catalog.yaml\nmodules:\n  - moduleId: fixture\n    displayName: Fixture\n    description: Fixture module\n    repository: https://github.com/Zalava/zalava-module-fixture\n    releaseIndexPath: releases/index.yaml\n";
  private static final String SHA = "a".repeat(40);

  @Test
  void resolvesPinnedUri() {
    var client =
        new MultiClient(CATALOG.getBytes(), ("[{\"sha\":\"" + SHA + "\"}]").getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    var result = locator.resolve("fixture");
    assertThat(result.moduleId()).isEqualTo("fixture");
    assertThat(result.manifestUri().toString()).contains(SHA);
  }

  @Test
  void rejectsUnknownModule() {
    var client =
        new MultiClient(CATALOG.getBytes(), ("[{\"sha\":\"" + SHA + "\"}]").getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
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

  @Test
  void rejectsCatalogHttpError() {
    var client = new MultiClient(null, new byte[0], 404);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Module locator request failed");
  }

  @Test
  void rejectsCatalogTooLarge() {
    byte[] big = new byte[256 * 1024 + 1];
    var client = new MultiClient(big, new byte[0], 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("maximum allowed size");
  }

  @Test
  void rejectsEmptyCommitList() {
    var client = new MultiClient(CATALOG.getBytes(), "[]".getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("did not return a module release-index revision");
  }

  @Test
  void rejectsNonHexCommitSha() {
    var badSha = "{\"sha\":\"" + "z".repeat(40) + "\"}";
    var client = new MultiClient(CATALOG.getBytes(), badSha.getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("did not return a module release-index revision");
  }

  @Test
  void rejectsInvalidCommitJson() {
    var client = new MultiClient(CATALOG.getBytes(), "not-json".getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Unable to parse GitHub module revision");
  }

  @Test
  void sendsAuthorizationWhenTokenProvided() {
    var client =
        new MultiClient(CATALOG.getBytes(), ("[{\"sha\":\"" + SHA + "\"}]").getBytes(), 200);
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            "test-token");
    locator.resolve("fixture");
    assertThat(client.catalogAuthorized()).isTrue();
    assertThat(client.commitsAuthorized()).isTrue();
  }

  @Test
  void rejectsManifestRequestInterrupted() {
    var client = new InterruptedClient();
    var locator =
        new JdkModuleLocatorReleaseLocator(
            client,
            URI.create("https://catalog.example"),
            new ModuleLocatorIndexLoader(),
            new ObjectMapper(),
            null);
    assertThatThrownBy(() -> locator.resolve("fixture"))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("interrupted");
  }

  private static final class InterruptedClient extends HttpClient {
    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws java.io.IOException, InterruptedException {
      throw new InterruptedException("test");
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

  private static final class MultiClient extends HttpClient {
    private final byte[] catalogBody;
    private final byte[] commitsBody;
    private final int statusCode;
    private boolean catalogAuthorized;
    private boolean commitsAuthorized;

    MultiClient(byte[] catalogBody, byte[] commitsBody, int statusCode) {
      this.catalogBody = catalogBody;
      this.commitsBody = commitsBody;
      this.statusCode = statusCode;
    }

    boolean catalogAuthorized() {
      return catalogAuthorized;
    }

    boolean commitsAuthorized() {
      return commitsAuthorized;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      boolean isGithubApi =
          request.uri().getHost() != null && request.uri().getHost().contains("api.github");
      if (isGithubApi) {
        commitsAuthorized = request.headers().firstValue("Authorization").isPresent();
      } else {
        catalogAuthorized = request.headers().firstValue("Authorization").isPresent();
      }
      if (statusCode != 200) {
        return new SimpleResponse<>(request, statusCode, null);
      }
      byte[] body = isGithubApi ? commitsBody : catalogBody;
      @SuppressWarnings("unchecked")
      T b = (T) (body != null ? body : new byte[0]);
      return new SimpleResponse<>(request, 200, b);
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

  record SimpleResponse<T>(HttpRequest request, int code, T body) implements HttpResponse<T> {
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
      return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
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
