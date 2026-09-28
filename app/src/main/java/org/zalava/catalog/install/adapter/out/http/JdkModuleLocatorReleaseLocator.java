package org.zalava.catalog.install.adapter.out.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.zalava.catalog.ModuleLocatorIndex;
import org.zalava.catalog.ModuleLocatorIndexLoader;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.ModuleLocatorReleaseLocator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resolves public locator entries and pins module release indexes to their Git commit. */
public final class JdkModuleLocatorReleaseLocator implements ModuleLocatorReleaseLocator {
  private static final int MAX_BYTES = 256 * 1024;
  private static final Pattern COMMIT = Pattern.compile("[0-9a-fA-F]{40}");

  private final HttpClient client;
  private final URI catalogUri;
  private final ModuleLocatorIndexLoader loader;
  private final ObjectMapper json;
  private final String accessToken;

  public JdkModuleLocatorReleaseLocator(URI catalogUri) {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        catalogUri,
        new ModuleLocatorIndexLoader(),
        new ObjectMapper(),
        null);
  }

  public JdkModuleLocatorReleaseLocator(URI catalogUri, String accessToken) {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        catalogUri,
        new ModuleLocatorIndexLoader(),
        new ObjectMapper(),
        accessToken);
  }

  JdkModuleLocatorReleaseLocator(
      HttpClient client,
      URI catalogUri,
      ModuleLocatorIndexLoader loader,
      ObjectMapper json,
      String accessToken) {
    this.client = client;
    this.catalogUri = requireHttps(catalogUri, "Module locator catalog URL");
    this.loader = loader;
    this.json = json;
    this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken;
  }

  @Override
  public List<Module> modules() {
    return loadCatalog().modules().stream()
        .map(module -> new Module(module.moduleId(), module.displayName(), module.description()))
        .sorted(Comparator.comparing(Module::displayName).thenComparing(Module::moduleId))
        .toList();
  }

  @Override
  public ResolvedModule resolve(String moduleId) {
    ModuleLocatorIndex.Module module =
        loadCatalog().modules().stream()
            .filter(candidate -> candidate.moduleId().equals(moduleId))
            .findFirst()
            .orElseThrow(
                () ->
                    new SourceModuleInstallationException(
                        "Module is not present in the module locator catalog: " + moduleId));
    String[] repository = repository(module.repository());
    return new ResolvedModule(
        module.moduleId(),
        module.displayName(),
        module.description(),
        immutableManifestUri(module, repository),
        URI.create("https://maven.pkg.github.com/" + repository[0] + "/" + repository[1]));
  }

  private ModuleLocatorIndex loadCatalog() {
    return loader.load(
        new String(
            request(catalogUri, "application/yaml, text/yaml, text/plain"),
            StandardCharsets.UTF_8));
  }

  private URI immutableManifestUri(ModuleLocatorIndex.Module module, String[] repository) {
    String encodedPath =
        URLEncoder.encode(module.releaseIndexPath(), StandardCharsets.UTF_8).replace("+", "%20");
    URI commitUri =
        URI.create(
            "https://api.github.com/repos/"
                + repository[0]
                + "/"
                + repository[1]
                + "/commits?path="
                + encodedPath
                + "&per_page=1");
    String revision;
    try {
      JsonNode commits = json.readTree(request(commitUri, "application/vnd.github+json"));
      revision = commits.isArray() && !commits.isEmpty() ? commits.get(0).path("sha").asText() : "";
    } catch (RuntimeException exception) {
      throw new SourceModuleInstallationException(
          "Unable to parse GitHub module revision", exception);
    }
    if (!COMMIT.matcher(revision).matches())
      throw new SourceModuleInstallationException(
          "GitHub did not return a module release-index revision");
    return URI.create(
        "https://raw.githubusercontent.com/"
            + repository[0]
            + "/"
            + repository[1]
            + "/"
            + revision
            + "/"
            + module.releaseIndexPath());
  }

  private byte[] request(URI uri, String accept) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(20))
              .GET()
              .header("Accept", accept)
              .header("User-Agent", "sea-module-installer");
      if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
      HttpResponse<byte[]> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200)
        throw new SourceModuleInstallationException(
            "Module locator request failed: HTTP " + response.statusCode());
      if (response.body().length > MAX_BYTES)
        throw new SourceModuleInstallationException(
            "Module locator response exceeds the maximum allowed size");
      return response.body();
    } catch (IOException exception) {
      throw new SourceModuleInstallationException("Unable to request module locator", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SourceModuleInstallationException(
          "Module locator request was interrupted", exception);
    }
  }

  private static String[] repository(URI value) {
    String[] parts = value.getPath().split("/");
    if (!"github.com".equalsIgnoreCase(value.getHost()) || parts.length != 3) {
      throw new SourceModuleInstallationException(
          "Module locator repository must be a GitHub repository");
    }
    return new String[] {parts[1], parts[2]};
  }

  private static URI requireHttps(URI value, String name) {
    if (value == null
        || !"https".equalsIgnoreCase(value.getScheme())
        || value.getHost() == null
        || value.getRawQuery() != null
        || value.getRawFragment() != null) {
      throw new IllegalArgumentException(name + " must be an HTTPS URL without query or fragment");
    }
    return value;
  }
}
