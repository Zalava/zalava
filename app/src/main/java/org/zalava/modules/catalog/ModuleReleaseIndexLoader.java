package org.zalava.modules.catalog;

import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.zalava.modules.catalog.install.ModuleArtifactRepository;

public final class ModuleReleaseIndexLoader {

  private static final Pattern VERSION =
      Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?");
  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern COORDINATE_SEGMENT =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

  public ModuleReleaseIndex load(String yaml) {
    return load(new StringReader(yaml));
  }

  public ModuleReleaseIndex load(Reader reader) {
    Map<String, Object> root = document(reader);
    if (integer(root.get("schemaVersion"), "schemaVersion") != 1) {
      throw invalid("schemaVersion", "must be 1");
    }
    String moduleId = text(root.get("moduleId"), "moduleId");
    List<Object> values = list(root.get("releases"), "releases", true);
    Set<String> versions = new HashSet<>();
    List<ModuleReleaseIndex.Release> releases = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      String path = "releases[" + index + "]";
      ModuleReleaseIndex.Release release = release(map(values.get(index), path), path);
      if (release.artifact().repository() instanceof ModuleArtifactRepository.GitHubReleaseAsset
          && !release.artifact().artifactId().equals(moduleId)) {
        throw invalid(path + ".artifact.artifactId", "must match module id");
      }
      if (!versions.add(release.version())) {
        throw invalid(path + ".version", "must be unique");
      }
      releases.add(release);
    }
    return new ModuleReleaseIndex(1, moduleId, releases);
  }

  private static ModuleReleaseIndex.Release release(Map<String, Object> value, String path) {
    String version = version(value.get("version"), path + ".version");
    String releaseTag = text(value.get("releaseTag"), path + ".releaseTag");
    if (!releaseTag.equals("v" + version)) {
      throw invalid(path + ".releaseTag", "must match the immutable version tag");
    }
    ModuleReleaseIndex.Artifact artifact =
        artifact(map(value.get("artifact"), path + ".artifact"), path + ".artifact", version, true);
    List<ModuleReleaseIndex.Artifact> runtimeArtifacts =
        runtimeArtifacts(value.get("runtimeArtifacts"), path, artifact);
    boolean artifactBundle = bool(value.get("artifactBundle"), path + ".artifactBundle", false);
    Map<String, Object> source = map(value.get("source"), path + ".source");
    if (artifact.repository() instanceof ModuleArtifactRepository.GitHubReleaseAsset github
        && !github
            .repositoryUri()
            .toString()
            .concat(".git")
            .equals(text(source.get("repository"), path + ".source.repository")))
      throw invalid(path + ".source.repository", "must match the artifact repository");
    Map<String, Object> compatibility = map(value.get("compatibility"), path + ".compatibility");
    Map<String, Object> security = map(value.get("security"), path + ".security");
    return new ModuleReleaseIndex.Release(
        version,
        releaseTag,
        artifact,
        runtimeArtifacts,
        artifactBundle,
        new ModuleReleaseIndex.Source(
            httpsUri(source.get("repository"), path + ".source.repository"),
            text(source.get("license"), path + ".source.license"),
            sourceRevision(source, path, artifact.repository() != null)),
        new ModuleReleaseIndex.Compatibility(
            text(compatibility.get("zalavaRuntime"), path + ".compatibility.zalavaRuntime")),
        new ModuleReleaseIndex.Security(
            strings(security.get("permissions"), path + ".security.permissions")));
  }

  private static List<ModuleReleaseIndex.Artifact> runtimeArtifacts(
      Object value, String path, ModuleReleaseIndex.Artifact primary) {
    if (value == null) {
      return List.of();
    }
    List<Object> values = list(value, path + ".runtimeArtifacts", false);
    Set<String> identities = new HashSet<>();
    identities.add(identity(primary));
    List<ModuleReleaseIndex.Artifact> artifacts = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      String artifactPath = path + ".runtimeArtifacts[" + index + "]";
      ModuleReleaseIndex.Artifact artifact =
          artifact(map(values.get(index), artifactPath), artifactPath, null, false);
      if (!identities.add(identity(artifact))) {
        throw invalid(artifactPath, "must not duplicate a primary or runtime artifact");
      }
      artifacts.add(artifact);
    }
    return List.copyOf(artifacts);
  }

  private static ModuleReleaseIndex.Artifact artifact(
      Map<String, Object> value,
      String path,
      String releaseVersion,
      boolean requireReleaseVersion) {
    String groupId = coordinate(value.get("groupId"), path + ".groupId");
    String artifactId = coordinate(value.get("artifactId"), path + ".artifactId");
    String artifactVersion = version(value.get("version"), path + ".version");
    if (requireReleaseVersion && !releaseVersion.equals(artifactVersion)) {
      throw invalid(path + ".version", "must match release version");
    }
    return new ModuleReleaseIndex.Artifact(
        groupId,
        artifactId,
        artifactVersion,
        sha256(value.get("sha256"), path + ".sha256"),
        repository(value, path, artifactId, artifactVersion));
  }

  private static ModuleArtifactRepository repository(
      Map<String, Object> value, String path, String artifactId, String version) {
    if (value.get("type") == null) return null;
    if (!"github-release-assets".equals(text(value.get("type"), path + ".type")))
      throw invalid(path + ".type", "must be github-release-assets");
    String tag = text(value.get("releaseTag"), path + ".releaseTag");
    String asset = text(value.get("assetName"), path + ".assetName");
    if (!tag.equals("v" + version) || !asset.equals(artifactId + "-" + version + ".jar"))
      throw invalid(path, "release tag and asset must match immutable artifact coordinates");
    try {
      return new ModuleArtifactRepository.GitHubReleaseAsset(
          artifactId, httpsUri(value.get("repositoryUri"), path + ".repositoryUri"), tag, asset);
    } catch (IllegalArgumentException exception) {
      throw invalid(path, exception.getMessage());
    }
  }

  private static String sourceRevision(Map<String, Object> source, String path, boolean required) {
    if (source.get("revision") == null && !required) return null;
    String revision = text(source.get("revision"), path + ".source.revision");
    if (!revision.matches("[0-9a-f]{40}"))
      throw invalid(path + ".source.revision", "must be a full immutable source commit");
    return revision;
  }

  private static String coordinate(Object value, String path) {
    String coordinate = text(value, path);
    if (!COORDINATE_SEGMENT.matcher(coordinate).matches()
        || coordinate.equals(".")
        || coordinate.equals("..")) {
      throw invalid(path, "must be a safe Maven coordinate segment");
    }
    return coordinate;
  }

  private static String identity(ModuleReleaseIndex.Artifact artifact) {
    return artifact.groupId() + ':' + artifact.artifactId() + ':' + artifact.version();
  }

  private static Map<String, Object> document(Reader reader) {
    try {
      return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(reader), "index");
    } catch (YAMLException exception) {
      throw invalid("index", "must be valid YAML", exception);
    }
  }

  private static Map<String, Object> map(Object value, String path) {
    if (!(value instanceof Map<?, ?> raw)) throw invalid(path, "must be an object");
    for (Object key : raw.keySet())
      if (!(key instanceof String)) throw invalid(path, "must use string keys");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) raw;
    return result;
  }

  private static List<Object> list(Object value, String path, boolean nonEmpty) {
    if (!(value instanceof List<?> raw)) throw invalid(path, "must be a list");
    if (nonEmpty && raw.isEmpty()) throw invalid(path, "must not be empty");
    return new ArrayList<>(raw);
  }

  private static List<String> strings(Object value, String path) {
    List<Object> values = list(value, path, false);
    List<String> result = new ArrayList<>();
    for (int index = 0; index < values.size(); index++)
      result.add(text(values.get(index), path + "[" + index + "]"));
    return result;
  }

  private static String text(Object value, String path) {
    if (!(value instanceof String string) || string.isBlank())
      throw invalid(path, "must be a non-blank string");
    return string;
  }

  private static int integer(Object value, String path) {
    if (!(value instanceof Integer integer)) throw invalid(path, "must be an integer");
    return integer;
  }

  private static boolean bool(Object value, String path, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean result)) {
      throw invalid(path, "must be a boolean");
    }
    return result;
  }

  private static String version(Object value, String path) {
    String version = text(value, path);
    if (!VERSION.matcher(version).matches()) throw invalid(path, "must be a semantic version");
    return version;
  }

  private static String sha256(Object value, String path) {
    String digest = text(value, path);
    if (!SHA_256.matcher(digest).matches())
      throw invalid(path, "must be a lowercase SHA-256 digest");
    return digest;
  }

  private static URI httpsUri(Object value, String path) {
    try {
      URI uri = new URI(text(value, path));
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
        throw invalid(path, "must be a valid HTTPS URI");
      return uri;
    } catch (URISyntaxException exception) {
      throw invalid(path, "must be a valid HTTPS URI", exception);
    }
  }

  private static SourceModuleIndexValidationException invalid(String path, String message) {
    return new SourceModuleIndexValidationException(path + " " + message);
  }

  private static SourceModuleIndexValidationException invalid(
      String path, String message, Throwable cause) {
    return new SourceModuleIndexValidationException(path + " " + message, cause);
  }
}
