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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Strict parser for the dedicated, public SEA module locator catalog. */
public final class ModuleLocatorIndexLoader {

  public ModuleLocatorIndex load(String yaml) {
    return load(new StringReader(yaml));
  }

  public ModuleLocatorIndex load(Reader reader) {
    Map<String, Object> root;
    try {
      root = map(new Yaml(new SafeConstructor(new LoaderOptions())).load(reader), "catalog");
    } catch (YAMLException exception) {
      throw invalid("catalog must be valid YAML", exception);
    }
    only(root, Set.of("schemaVersion", "repository", "modules"), "catalog");
    if (integer(root.get("schemaVersion"), "schemaVersion") != 1)
      throw invalid("schemaVersion must be 1");
    Map<String, Object> repository = map(root.get("repository"), "repository");
    only(repository, Set.of("type", "indexRepository", "indexPath"), "repository");
    if (!"module-locator".equals(text(repository.get("type"), "repository.type"))) {
      throw invalid("repository.type must be module-locator");
    }
    URI indexRepository =
        githubRepository(repository.get("indexRepository"), "repository.indexRepository");
    String indexPath = yamlPath(repository.get("indexPath"), "repository.indexPath");
    List<Object> values = list(root.get("modules"), "modules", true);
    Set<String> ids = new HashSet<>();
    List<ModuleLocatorIndex.Module> modules = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      String path = "modules[" + index + "]";
      Map<String, Object> value = map(values.get(index), path);
      only(
          value,
          Set.of("moduleId", "displayName", "description", "repository", "releaseIndexPath"),
          path);
      String moduleId = text(value.get("moduleId"), path + ".moduleId");
      if (!ids.add(moduleId)) throw invalid(path + ".moduleId must be unique");
      modules.add(
          new ModuleLocatorIndex.Module(
              moduleId,
              text(value.get("displayName"), path + ".displayName"),
              text(value.get("description"), path + ".description"),
              githubRepository(value.get("repository"), path + ".repository"),
              yamlPath(value.get("releaseIndexPath"), path + ".releaseIndexPath")));
    }
    return new ModuleLocatorIndex(1, indexRepository, indexPath, modules);
  }

  private static URI githubRepository(Object value, String path) {
    URI uri = httpsUri(value, path);
    String[] parts = uri.getPath().split("/");
    if (!"github.com".equalsIgnoreCase(uri.getHost())
        || parts.length != 3
        || parts[1].isBlank()
        || parts[2].isBlank()) {
      throw invalid(path + " must be an HTTPS GitHub repository URI");
    }
    return uri;
  }

  private static String yamlPath(Object value, String path) {
    String result = text(value, path);
    if (!result.endsWith(".yaml") && !result.endsWith(".yml"))
      throw invalid(path + " must name a YAML file");
    for (String segment : result.split("/"))
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment))
        throw invalid(path + " must be a relative path without dot segments");
    return result;
  }

  private static URI httpsUri(Object value, String path) {
    try {
      URI uri = new URI(text(value, path));
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null)
        throw invalid(path + " must be an HTTPS URI without query or fragment");
      return uri;
    } catch (URISyntaxException exception) {
      throw invalid(path + " must be an HTTPS URI", exception);
    }
  }

  private static Map<String, Object> map(Object value, String path) {
    if (!(value instanceof Map<?, ?> raw)) throw invalid(path + " must be an object");
    for (Object key : raw.keySet())
      if (!(key instanceof String)) throw invalid(path + " must use string keys");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) raw;
    return result;
  }

  private static void only(Map<String, Object> value, Set<String> allowed, String path) {
    for (String key : value.keySet())
      if (!allowed.contains(key)) throw invalid(path + " contains unsupported field: " + key);
  }

  private static List<Object> list(Object value, String path, boolean nonEmpty) {
    if (!(value instanceof List<?> raw) || (nonEmpty && raw.isEmpty()))
      throw invalid(path + " must be a non-empty list");
    return new ArrayList<>(raw);
  }

  private static String text(Object value, String path) {
    if (!(value instanceof String text) || text.isBlank())
      throw invalid(path + " must be a non-blank string");
    return text;
  }

  private static int integer(Object value, String path) {
    if (!(value instanceof Integer number)) throw invalid(path + " must be an integer");
    return number;
  }

  private static SourceModuleIndexValidationException invalid(String message) {
    return new SourceModuleIndexValidationException(message);
  }

  private static SourceModuleIndexValidationException invalid(String message, Throwable cause) {
    return new SourceModuleIndexValidationException(message, cause);
  }
}
