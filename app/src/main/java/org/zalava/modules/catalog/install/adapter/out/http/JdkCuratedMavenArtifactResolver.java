package org.zalava.modules.catalog.install.adapter.out.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.CuratedMavenArtifactResolver;

public final class JdkCuratedMavenArtifactResolver implements CuratedMavenArtifactResolver {

  private static final long MAX_ARTIFACT_BYTES = 100L * 1024 * 1024;
  private static final Pattern COORDINATE_SEGMENT =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
  private static final Pattern SHA_256 = Pattern.compile("^([0-9a-f]{64})(?:\\s+.*)?$");

  private final HttpClient httpClient;
  private final Path downloadsRoot;
  private final Map<String, Credentials> credentials;

  public JdkCuratedMavenArtifactResolver(Path workspace) {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        workspace,
        Map.of());
  }

  public JdkCuratedMavenArtifactResolver(Path workspace, Map<String, Credentials> credentials) {
    this(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        workspace,
        credentials);
  }

  JdkCuratedMavenArtifactResolver(
      HttpClient httpClient, Path workspace, Map<String, Credentials> credentials) {
    this.httpClient = httpClient;
    this.credentials = Map.copyOf(credentials);
    this.downloadsRoot =
        workspace
            .toAbsolutePath()
            .normalize()
            .resolve("source-module-installation")
            .resolve("downloads");
  }

  @Override
  public ResolvedArtifact resolve(Request request) {
    URI artifactUri = artifactUri(request);
    String expectedDigest = checksum(checksumUri(artifactUri), request.repositoryId());
    Path temporary = temporaryFile();
    try {
      String actualDigest = download(artifactUri, temporary, request.repositoryId());
      if (!actualDigest.equals(expectedDigest)) {
        throw new SourceModuleInstallationException(
            "Curated Maven artifact digest does not match repository checksum");
      }
      return new ResolvedArtifact(temporary.toString(), actualDigest);
    } catch (RuntimeException exception) {
      delete(temporary);
      throw exception;
    }
  }

  @Override
  public void discard(ResolvedArtifact artifact) {
    if (artifact == null || artifact.path() == null) {
      return;
    }
    Path path = Path.of(artifact.path()).toAbsolutePath().normalize();
    if (path.startsWith(downloadsRoot)) {
      delete(path);
    }
  }

  private URI artifactUri(Request request) {
    if (request == null || request.repositoryUrl() == null || request.artifact() == null) {
      throw new SourceModuleInstallationException("Curated Maven artifact request is required");
    }
    URI repository = request.repositoryUrl();
    if (!"https".equalsIgnoreCase(repository.getScheme()) || repository.getHost() == null) {
      throw new SourceModuleInstallationException("Curated Maven repository must use HTTPS");
    }
    SourceModuleIndex.Artifact artifact = request.artifact();
    String groupPath = artifact.groupId().replace('.', '/');
    validateSegments(groupPath, "artifact group id");
    validateSegment(artifact.artifactId(), "artifact id");
    validateSegment(artifact.version(), "artifact version");
    String filename = artifact.artifactId() + "-" + artifact.version() + ".jar";
    return repository.resolve(
        trimTrailingSlash(repository.getPath())
            + "/"
            + groupPath
            + "/"
            + artifact.artifactId()
            + "/"
            + artifact.version()
            + "/"
            + filename);
  }

  private static URI checksumUri(URI artifactUri) {
    return URI.create(artifactUri + ".sha256");
  }

  private String checksum(URI uri, String repositoryId) {
    HttpResponse<String> response =
        send(
            request(uri, Duration.ofSeconds(20), repositoryId).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new SourceModuleInstallationException(
          "Curated Maven checksum request failed: HTTP " + response.statusCode());
    }
    Matcher matcher = SHA_256.matcher(response.body().trim());
    if (!matcher.matches()) {
      throw new SourceModuleInstallationException(
          "Curated Maven checksum must be a SHA-256 digest");
    }
    return "sha256:" + matcher.group(1);
  }

  private String download(URI uri, Path destination, String repositoryId) {
    HttpResponse<InputStream> response =
        send(
            request(uri, Duration.ofSeconds(60), repositoryId).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() >= 300 && response.statusCode() < 400) {
      try {
        response.body().close();
      } catch (IOException ignored) {
      }
      URI redirect = redirect(response, uri);
      response =
          send(
              HttpRequest.newBuilder(redirect).timeout(Duration.ofSeconds(60)).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
    }
    if (response.statusCode() != 200) {
      throw new SourceModuleInstallationException(
          "Curated Maven artifact request failed: HTTP " + response.statusCode());
    }
    try (InputStream input = response.body();
        var output = Files.newOutputStream(destination)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > MAX_ARTIFACT_BYTES) {
          throw new SourceModuleInstallationException(
              "Curated Maven artifact exceeds the maximum allowed size");
        }
        digest.update(buffer, 0, read);
        output.write(buffer, 0, read);
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to download curated Maven artifact", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static URI redirect(HttpResponse<?> response, URI original) {
    String location =
        response
            .headers()
            .firstValue("Location")
            .orElseThrow(
                () ->
                    new SourceModuleInstallationException(
                        "Curated Maven artifact redirect is missing Location"));
    URI target = original.resolve(location);
    if (!"https".equalsIgnoreCase(target.getScheme()) || target.getHost() == null) {
      throw new SourceModuleInstallationException("Curated Maven artifact redirect must use HTTPS");
    }
    return target;
  }

  private HttpRequest.Builder request(URI uri, Duration timeout, String repositoryId) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout);
    Credentials credential = credentials.get(repositoryId);
    if (credential != null) request.header("Authorization", credential.basicAuthorization());
    return request;
  }

  public record Credentials(String username, String token) {
    public Credentials {
      if (username == null || username.isBlank() || token == null || token.isBlank()) {
        throw new IllegalArgumentException("repository credentials require username and token");
      }
    }

    String basicAuthorization() {
      return "Basic "
          + Base64.getEncoder()
              .encodeToString((username + ":" + token).getBytes(StandardCharsets.UTF_8));
    }
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      return httpClient.send(request, handler);
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to request curated Maven repository", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SourceModuleInstallationException(
          "Curated Maven repository request was interrupted", exception);
    }
  }

  private Path temporaryFile() {
    try {
      if (Files.exists(downloadsRoot, LinkOption.NOFOLLOW_LINKS)
          && Files.isSymbolicLink(downloadsRoot)) {
        throw new SourceModuleInstallationException(
            "Curated Maven download directory must not be a symbolic link");
      }
      Files.createDirectories(downloadsRoot);
      return Files.createTempFile(downloadsRoot, "artifact-", ".jar");
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to prepare curated Maven download directory", exception);
    }
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Preserve the primary resolution error.
    }
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static void validateSegments(String value, String field) {
    for (String segment : value.split("/")) {
      validateSegment(segment, field);
    }
  }

  private static void validateSegment(String value, String field) {
    if (value == null
        || !COORDINATE_SEGMENT.matcher(value).matches()
        || ".".equals(value)
        || "..".equals(value)) {
      throw new SourceModuleInstallationException("Invalid curated Maven " + field);
    }
  }
}
