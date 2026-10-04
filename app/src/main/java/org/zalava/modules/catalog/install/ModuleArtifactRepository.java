package org.zalava.modules.catalog.install;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed, allow-listed source for an installable module binary.
 *
 * <p>Catalog parsing must construct one of these forms before an adapter performs any network
 * request. In particular, a repository URL alone never selects an installer protocol.
 */
public sealed interface ModuleArtifactRepository
    permits ModuleArtifactRepository.Maven, ModuleArtifactRepository.GitHubReleaseAsset {

  String repositoryId();

  record Maven(String repositoryId, URI baseUri) implements ModuleArtifactRepository {
    public Maven {
      requireId(repositoryId);
      requireHttps(baseUri, "Maven repository");
    }
  }

  record GitHubReleaseAsset(
      String repositoryId, URI repositoryUri, String releaseTag, String assetName)
      implements ModuleArtifactRepository {
    private static final Pattern RELEASE_TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern ASSET_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,255}");

    public GitHubReleaseAsset {
      requireId(repositoryId);
      requireHttps(repositoryUri, "GitHub release repository");
      if (!"github.com".equalsIgnoreCase(repositoryUri.getHost())) {
        throw new IllegalArgumentException("GitHub release repository must use github.com");
      }
      if (!repositoryUri.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
          || repositoryUri.getRawQuery() != null
          || repositoryUri.getRawFragment() != null
          || repositoryUri.getPort() != -1) {
        throw new IllegalArgumentException(
            "GitHub release repository must name one source repository");
      }
      if (releaseTag == null || !RELEASE_TAG.matcher(releaseTag).matches()) {
        throw new IllegalArgumentException("GitHub release tag is invalid");
      }
      if (assetName == null || !ASSET_NAME.matcher(assetName).matches()) {
        throw new IllegalArgumentException("GitHub release asset name is invalid");
      }
    }
  }

  private static void requireId(String value) {
    if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
      throw new IllegalArgumentException("repository id is invalid");
    }
  }

  private static void requireHttps(URI value, String subject) {
    Objects.requireNonNull(value, subject + " is required");
    if (!"https".equalsIgnoreCase(value.getScheme())
        || value.getHost() == null
        || value.getUserInfo() != null) {
      throw new IllegalArgumentException(
          subject + " must be an HTTPS URI without user information");
    }
  }
}
