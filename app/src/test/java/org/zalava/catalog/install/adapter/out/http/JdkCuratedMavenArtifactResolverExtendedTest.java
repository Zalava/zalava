package org.zalava.catalog.install.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
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
import org.zalava.catalog.install.application.port.out.CuratedMavenArtifactResolver.ResolvedArtifact;

class JdkCuratedMavenArtifactResolverExtendedTest {

  private static final SourceModuleIndex.Artifact ARTIFACT =
      new SourceModuleIndex.Artifact("ai.sea.modules", "zalava-module-fixture", "1.0.0");

  @TempDir Path workspace;

  @Test
  void resolvesWithTrailingSlashAndChecksSha() throws Exception {
    byte[] bytes = "ok".getBytes();
    String digest = sha256(bytes);
    var client = new ArtifactClient(bytes, digest, 200);
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    var result =
        resolver.resolve(new Request("curated", URI.create("https://repo.example/"), ARTIFACT));
    assertThat(result.sha256Digest()).isEqualTo(digest);
  }

  @Test
  void followsRedirectDuringDownload() throws Exception {
    byte[] bytes = "redirected".getBytes();
    String digest = sha256(bytes);
    var client =
        new DownloadRedirectClient(URI.create("https://final.example/art.jar"), bytes, digest);
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    var result =
        resolver.resolve(new Request("curated", URI.create("https://repo.example/"), ARTIFACT));
    assertThat(Files.readAllBytes(Path.of(result.path()))).isEqualTo(bytes);
  }

  @Test
  void rejectsDownloadRedirectMissingLocation() {
    var client = new RedirectDownloadClient(null);
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("missing Location");
  }

  @Test
  void rejectsDownloadRedirectToHttp() {
    var client = new RedirectDownloadClient(URI.create("http://insecure.example/art.jar"));
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("redirect must use HTTPS");
  }

  @Test
  void rejectsArtifactDownloadHttpError() {
    var client = new FailingDownloadClient(403);
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of());
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Request("curated", URI.create("https://repo.example/"), ARTIFACT)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("artifact request failed");
  }

  @Test
  void respectsCredentialsInRequest() throws Exception {
    byte[] bytes = "cred".getBytes();
    String digest = sha256(bytes);
    var creds = new JdkCuratedMavenArtifactResolver.Credentials("user", "pass");
    var client = new ArtifactClient(bytes, digest, 200);
    var resolver = new JdkCuratedMavenArtifactResolver(client, workspace, Map.of("curated", creds));
    resolver.resolve(new Request("curated", URI.create("https://repo.example/"), ARTIFACT));
    assertThat(client.lastAuthorization()).startsWith("Basic ");
  }

  @Test
  void discardHandlesNullAndNonManagedPaths() {
    var resolver = new JdkCuratedMavenArtifactResolver(workspace);
    resolver.discard(null);
    resolver.discard(new ResolvedArtifact(null, null));
    resolver.discard(new ResolvedArtifact("/tmp/outside/artifact.jar", "sha256:abc"));
    Path managed = workspace.resolve("source-module-installation/downloads/artifact.jar");
    resolver.discard(new ResolvedArtifact(managed.toString(), "sha256:abc"));
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

  /**
   * Helper: if the URI ends with .sha256, return a String-based response (checksum path). Otherwise
   * return an InputStream-based response (download path).
   */
  @SuppressWarnings("unchecked")
  static <T> HttpResponse<T> typedResponse(
      HttpRequest request, int code, byte[] data, String stringBody, URI location) {
    if (stringBody != null) {
      return (HttpResponse<T>) new StringResponse(request, code, stringBody);
    }
    if (location != null) {
      return (HttpResponse<T>)
          new RedirectResp(
              request,
              code,
              new ByteArrayInputStream(new byte[0]),
              HttpHeaders.of(
                  Map.of("Location", java.util.List.of(location.toString())), (a, b) -> true));
    }
    return (HttpResponse<T>)
        new RedirectResp(
            request,
            code,
            new ByteArrayInputStream(new byte[0]),
            HttpHeaders.of(Map.of(), (a, b) -> true));
  }

  // --- Stub clients ---

  static class ArtifactClient extends HttpClient {
    private final byte[] body;
    private final String digest;
    private final int status;
    private String lastAuthorization;

    ArtifactClient(byte[] body, String digest, int status) {
      this.body = body;
      this.digest = digest;
      this.status = status;
    }

    String lastAuthorization() {
      return lastAuthorization;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      lastAuthorization = request.headers().firstValue("Authorization").orElse(null);
      if (request.uri().getPath().endsWith(".sha256"))
        return (HttpResponse<T>)
            new StringResponse(request, 200, digest.substring("sha256:".length()));
      return (HttpResponse<T>) new InputStreamResponse(request, status, body);
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

  static class DownloadRedirectClient extends HttpClient {
    private final URI target;
    private final byte[] body;
    private final String digest;

    DownloadRedirectClient(URI target, byte[] body, String digest) {
      this.target = target;
      this.body = body;
      this.digest = digest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (request.uri().getPath().endsWith(".sha256"))
        return (HttpResponse<T>)
            new StringResponse(request, 200, digest.substring("sha256:".length()));
      if (request.uri().getHost().equals("repo.example"))
        return (HttpResponse<T>)
            new RedirectResp(
                request,
                302,
                new ByteArrayInputStream(new byte[0]),
                HttpHeaders.of(
                    Map.of("Location", java.util.List.of(target.toString())), (a, b) -> true));
      return (HttpResponse<T>) new InputStreamResponse(request, 200, body);
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

  static class RedirectDownloadClient extends HttpClient {
    private final URI location;

    RedirectDownloadClient(URI location) {
      this.location = location;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (request.uri().getPath().endsWith(".sha256"))
        return (HttpResponse<T>)
            new StringResponse(
                request, 200, sha256("fixture".getBytes()).substring("sha256:".length()));
      HttpHeaders hdrs =
          location != null
              ? HttpHeaders.of(
                  Map.of("Location", java.util.List.of(location.toString())), (a, b) -> true)
              : HttpHeaders.of(Map.of(), (a, b) -> true);
      return (HttpResponse<T>)
          new RedirectResp(request, 302, new ByteArrayInputStream(new byte[0]), hdrs);
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

  static class FailingDownloadClient extends HttpClient {
    private final int status;

    FailingDownloadClient(int status) {
      this.status = status;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (request.uri().getPath().endsWith(".sha256"))
        return (HttpResponse<T>)
            new StringResponse(
                request, 200, sha256("fixture".getBytes()).substring("sha256:".length()));
      return (HttpResponse<T>) new InputStreamResponse(request, status, new byte[0]);
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

  /** Returns String body (for checksum handler). */
  static class StringResponse implements HttpResponse<Object> {
    private final HttpRequest request;
    private final int code;
    private final String body;

    StringResponse(HttpRequest request, int code, String body) {
      this.request = request;
      this.code = code;
      this.body = body;
    }

    public int statusCode() {
      return code;
    }

    public Object body() {
      return body;
    }

    public HttpRequest request() {
      return request;
    }

    public Optional<HttpResponse<Object>> previousResponse() {
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

  /** Returns InputStream body (for download handler). */
  static class InputStreamResponse implements HttpResponse<Object> {
    private final HttpRequest request;
    private final int code;
    private final ByteArrayInputStream body;

    InputStreamResponse(HttpRequest request, int code, byte[] data) {
      this.request = request;
      this.code = code;
      this.body = new ByteArrayInputStream(data);
    }

    public int statusCode() {
      return code;
    }

    public Object body() {
      return body;
    }

    public HttpRequest request() {
      return request;
    }

    public Optional<HttpResponse<Object>> previousResponse() {
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

  /** Returns closeable body with Location header (for redirect during download). */
  static class RedirectResp implements HttpResponse<Object> {
    private final HttpRequest request;
    private final int code;
    private final ByteArrayInputStream body;
    private final HttpHeaders headers;

    RedirectResp(HttpRequest request, int code, ByteArrayInputStream body, HttpHeaders headers) {
      this.request = request;
      this.code = code;
      this.body = body;
      this.headers = headers;
    }

    public int statusCode() {
      return code;
    }

    public Object body() {
      return body;
    }

    public HttpRequest request() {
      return request;
    }

    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
    }

    public HttpHeaders headers() {
      return headers;
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
