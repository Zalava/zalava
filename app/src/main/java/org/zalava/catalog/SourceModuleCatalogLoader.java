package org.zalava.catalog;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public class SourceModuleCatalogLoader {

  static final int SUPPORTED_SCHEMA_VERSION = 1;

  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern REPOSITORY_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  private final SourceModuleIndexLoader indexLoader;

  public SourceModuleCatalogLoader() {
    this(new SourceModuleIndexLoader());
  }

  SourceModuleCatalogLoader(SourceModuleIndexLoader indexLoader) {
    this.indexLoader = indexLoader;
  }

  public SourceModuleCatalog load(Path path) throws IOException {
    Path catalogPath = path.toAbsolutePath().normalize();
    Path baseDirectory = catalogPath.getParent();
    if (baseDirectory == null) {
      throw invalid("catalog", "must have a parent directory");
    }
    try (Reader reader = Files.newBufferedReader(catalogPath)) {
      return load(reader, baseDirectory);
    }
  }

  SourceModuleCatalog load(Reader reader, Path baseDirectory) throws IOException {
    Object document;
    try {
      document = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
    } catch (YAMLException exception) {
      throw invalid("catalog", "must be valid YAML", exception);
    }

    Map<String, Object> root = map(document, "catalog");
    int schemaVersion = integer(root.get("schemaVersion"), "schemaVersion");
    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      throw invalid("schemaVersion", "must be " + SUPPORTED_SCHEMA_VERSION);
    }

    SourceModuleCatalog.Repository repository =
        repository(map(root.get("repository"), "repository"), "repository");
    List<SourceModuleCatalog.MavenRepository> mavenRepositories =
        mavenRepositories(root.get("mavenRepositories"), "mavenRepositories");
    List<Object> entries = list(root.get("entries"), "entries", true);
    Set<String> moduleIds = new HashSet<>();
    Set<String> paths = new HashSet<>();
    List<SourceModuleCatalog.Entry> parsedEntries = new ArrayList<>();
    Path normalizedBaseDirectory = baseDirectory.toAbsolutePath().normalize();
    for (int index = 0; index < entries.size(); index++) {
      String entryPath = "entries[" + index + "]";
      SourceModuleCatalog.Entry entry =
          entry(map(entries.get(index), entryPath), entryPath, normalizedBaseDirectory);
      requireUnique(moduleIds, entry.moduleId(), entryPath + ".moduleId");
      requireUnique(paths, entry.path(), entryPath + ".path");
      parsedEntries.add(entry);
    }
    return new SourceModuleCatalog(schemaVersion, repository, mavenRepositories, parsedEntries);
  }

  private SourceModuleCatalog.Repository repository(Map<String, Object> value, String path) {
    String type = text(value.get("type"), path + ".type");
    if (!"source".equals(type)) {
      throw invalid(path + ".type", "must be source");
    }
    String indexRepository =
        httpsUri(value.get("indexRepository"), path + ".indexRepository").toString();
    String indexPath = text(value.get("indexPath"), path + ".indexPath");
    validateRelativeYamlPath(indexPath, path + ".indexPath");
    return new SourceModuleCatalog.Repository(type, indexRepository, indexPath);
  }

  private List<SourceModuleCatalog.MavenRepository> mavenRepositories(Object value, String path) {
    List<Object> repositories = list(value, path, true);
    Set<String> repositoryIds = new HashSet<>();
    Set<String> urls = new HashSet<>();
    List<SourceModuleCatalog.MavenRepository> parsedRepositories = new ArrayList<>();
    for (int index = 0; index < repositories.size(); index++) {
      String repositoryPath = path + "[" + index + "]";
      SourceModuleCatalog.MavenRepository repository =
          mavenRepository(map(repositories.get(index), repositoryPath), repositoryPath);
      requireUnique(repositoryIds, repository.repositoryId(), repositoryPath + ".repositoryId");
      requireUnique(urls, repository.url(), repositoryPath + ".url");
      parsedRepositories.add(repository);
    }
    return parsedRepositories;
  }

  private SourceModuleCatalog.MavenRepository mavenRepository(
      Map<String, Object> value, String path) {
    String repositoryId = text(value.get("repositoryId"), path + ".repositoryId");
    if (!REPOSITORY_ID.matcher(repositoryId).matches()) {
      throw invalid(path + ".repositoryId", "must be a lowercase repository id");
    }
    String url = httpsUri(value.get("url"), path + ".url").toString();
    return new SourceModuleCatalog.MavenRepository(repositoryId, url);
  }

  private SourceModuleCatalog.Entry entry(
      Map<String, Object> value, String path, Path baseDirectory) throws IOException {
    String moduleId = text(value.get("moduleId"), path + ".moduleId");
    String indexPath = text(value.get("path"), path + ".path");
    String sha256 = sha256(value.get("sha256"), path + ".sha256");
    Path resolvedIndexPath = resolveIndexPath(indexPath, path + ".path", baseDirectory);

    if (!Files.isRegularFile(resolvedIndexPath)) {
      throw invalid(path + ".path", "must reference a regular file");
    }
    String actualSha256 = sha256(resolvedIndexPath);
    if (!actualSha256.equals(sha256)) {
      throw invalid(path + ".sha256", "must match referenced index content");
    }

    SourceModuleIndex index;
    try {
      index = indexLoader.load(resolvedIndexPath);
    } catch (SourceModuleIndexValidationException exception) {
      throw invalid(path + ".index", "must reference a valid source module index", exception);
    }
    boolean containsModule =
        index.modules().stream().anyMatch(module -> module.moduleId().equals(moduleId));
    if (!containsModule) {
      throw invalid(path + ".moduleId", "must be present in referenced index");
    }

    return new SourceModuleCatalog.Entry(moduleId, indexPath, sha256, index);
  }

  private static Path resolveIndexPath(String value, String path, Path baseDirectory) {
    Path relativePath = Path.of(value);
    validateRelativeYamlPath(relativePath, path);
    Path resolved = baseDirectory.resolve(relativePath).normalize();
    if (!resolved.startsWith(baseDirectory)) {
      throw invalid(path, "must stay within the catalog directory");
    }
    return resolved;
  }

  private static void validateRelativeYamlPath(String value, String path) {
    validateRelativeYamlPath(Path.of(value), path);
  }

  private static void validateRelativeYamlPath(Path relativePath, String path) {
    if (relativePath.isAbsolute()) {
      throw invalid(path, "must be relative");
    }
    for (Path segment : relativePath) {
      String name = segment.toString();
      if (".".equals(name) || "..".equals(name)) {
        throw invalid(path, "must not contain dot segments");
      }
    }
    String fileName =
        relativePath.getFileName() == null ? "" : relativePath.getFileName().toString();
    if (!fileName.endsWith(".yaml") && !fileName.endsWith(".yml")) {
      throw invalid(path, "must reference a YAML file");
    }
  }

  private static URI httpsUri(Object value, String path) {
    String uri = text(value, path);
    URI parsed;
    try {
      parsed = new URI(uri);
    } catch (URISyntaxException exception) {
      throw invalid(path, "must be a valid HTTPS URI", exception);
    }
    if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
      throw invalid(path, "must be a valid HTTPS URI");
    }
    return parsed;
  }

  private static String sha256(Path path) throws IOException {
    MessageDigest digest = sha256Digest();
    try (var input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static Map<String, Object> map(Object value, String path) {
    if (!(value instanceof Map<?, ?> rawMap)) {
      throw invalid(path, "must be an object");
    }
    for (Object key : rawMap.keySet()) {
      if (!(key instanceof String)) {
        throw invalid(path, "must use string keys");
      }
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) rawMap;
    return map;
  }

  private static List<Object> list(Object value, String path, boolean requireNonEmpty) {
    if (!(value instanceof List<?> rawList)) {
      throw invalid(path, "must be a list");
    }
    if (requireNonEmpty && rawList.isEmpty()) {
      throw invalid(path, "must not be empty");
    }
    return new ArrayList<>(rawList);
  }

  private static String text(Object value, String path) {
    if (!(value instanceof String string) || string.isBlank()) {
      throw invalid(path, "must be a non-blank string");
    }
    return string;
  }

  private static int integer(Object value, String path) {
    if (!(value instanceof Integer integer)) {
      throw invalid(path, "must be an integer");
    }
    return integer;
  }

  private static String sha256(Object value, String path) {
    String digest = text(value, path);
    if (!SHA256.matcher(digest).matches()) {
      throw invalid(path, "must be a lowercase SHA-256 digest");
    }
    return digest;
  }

  private static void requireUnique(Set<String> values, String value, String path) {
    if (!values.add(value)) {
      throw invalid(path, "must be unique");
    }
  }

  private static SourceModuleCatalogValidationException invalid(String path, String message) {
    return new SourceModuleCatalogValidationException(path + " " + message);
  }

  private static SourceModuleCatalogValidationException invalid(
      String path, String message, Throwable cause) {
    return new SourceModuleCatalogValidationException(path + " " + message, cause);
  }
}
