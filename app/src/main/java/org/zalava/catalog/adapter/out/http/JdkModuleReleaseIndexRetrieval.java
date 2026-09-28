package org.zalava.catalog.adapter.out.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.ModuleReleaseIndexLoader;
import org.zalava.catalog.application.port.out.ModuleReleaseIndexRetrieval;
import org.zalava.catalog.install.SourceModuleInstallationException;

/** Bounded JDK HTTP adapter for immutable release-manifest retrieval. */
public final class JdkModuleReleaseIndexRetrieval implements ModuleReleaseIndexRetrieval {
  private static final int MAX_BYTES = 256 * 1024;
  private final HttpClient client;
  private final ModuleReleaseIndexLoader loader;
  private final String accessToken;

  public JdkModuleReleaseIndexRetrieval() {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        new ModuleReleaseIndexLoader(),
        null);
  }

  public JdkModuleReleaseIndexRetrieval(String accessToken) {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        new ModuleReleaseIndexLoader(),
        accessToken);
  }

  JdkModuleReleaseIndexRetrieval(
      HttpClient client, ModuleReleaseIndexLoader loader, String accessToken) {
    this.client = client;
    this.loader = loader;
    this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken;
  }

  @Override
  public ModuleReleaseIndex load(URI uri, String bearerToken) {
    if (uri == null
        || !"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new SourceModuleInstallationException(
          "Module release index URL must be an HTTPS URL without query or fragment");
    }
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .header("Accept", "application/yaml, text/yaml, text/plain");
    String token = bearerToken == null || bearerToken.isBlank() ? accessToken : bearerToken;
    if (token != null) request.header("Authorization", "Bearer " + token);
    try {
      HttpResponse<byte[]> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200)
        throw new SourceModuleInstallationException(
            "Module release index request failed: HTTP " + response.statusCode());
      if (response.body().length > MAX_BYTES)
        throw new SourceModuleInstallationException(
            "Module release index exceeds the maximum allowed size");
      return loader.load(new String(response.body(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to request module release index", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SourceModuleInstallationException(
          "Module release index request was interrupted", exception);
    }
  }
}
