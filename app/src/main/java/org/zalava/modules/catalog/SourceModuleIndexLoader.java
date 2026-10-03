package org.zalava.modules.catalog;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public class SourceModuleIndexLoader {

  static final int SUPPORTED_SCHEMA_VERSION = 1;

  public SourceModuleIndex load(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path)) {
      return load(reader);
    }
  }

  public SourceModuleIndex load(String yaml) {
    return load(new StringReader(yaml));
  }

  public SourceModuleIndex load(Reader reader) {
    Object document;
    try {
      document = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
    } catch (YAMLException exception) {
      throw invalid("index", "must be valid YAML", exception);
    }

    Map<String, Object> root = map(document, "index");
    int schemaVersion = integer(root.get("schemaVersion"), "schemaVersion");
    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      throw invalid("schemaVersion", "must be " + SUPPORTED_SCHEMA_VERSION);
    }

    List<Object> modules = list(root.get("modules"), "modules", true);
    Set<String> moduleIds = new HashSet<>();
    List<SourceModuleIndex.Module> parsedModules = new ArrayList<>();
    for (int index = 0; index < modules.size(); index++) {
      String path = "modules[" + index + "]";
      SourceModuleIndex.Module module = module(map(modules.get(index), path), path);
      requireUnique(moduleIds, module.moduleId(), path + ".moduleId");
      parsedModules.add(module);
    }
    return new SourceModuleIndex(schemaVersion, parsedModules);
  }

  private SourceModuleIndex.Module module(Map<String, Object> value, String path) {
    String moduleId = text(value.get("moduleId"), path + ".moduleId");
    String version = text(value.get("version"), path + ".version");
    Map<String, Object> artifact = map(value.get("artifact"), path + ".artifact");
    String artifactVersion = text(artifact.get("version"), path + ".artifact.version");
    if (!version.equals(artifactVersion)) {
      throw invalid(path + ".artifact.version", "must match module version");
    }

    return new SourceModuleIndex.Module(
        moduleId,
        version,
        text(value.get("displayName"), path + ".displayName"),
        text(value.get("description"), path + ".description"),
        httpsUri(value.get("supportUrl"), path + ".supportUrl"),
        new SourceModuleIndex.Artifact(
            text(artifact.get("groupId"), path + ".artifact.groupId"),
            text(artifact.get("artifactId"), path + ".artifact.artifactId"),
            artifactVersion),
        source(map(value.get("source"), path + ".source"), path + ".source"),
        build(map(value.get("build"), path + ".build"), path + ".build"),
        new SourceModuleIndex.Compatibility(
            text(
                map(value.get("compatibility"), path + ".compatibility").get("zalavaRuntime"),
                path + ".compatibility.zalavaRuntime")),
        map(value.get("configurationSchema"), path + ".configurationSchema"),
        factories(value.get("factories"), path + ".factories"),
        operations(value.get("operations"), path + ".operations"),
        security(map(value.get("security"), path + ".security"), path + ".security"));
  }

  private SourceModuleIndex.Source source(Map<String, Object> value, String path) {
    URI repositoryUri = httpsUri(value.get("repository"), path + ".repository");
    return new SourceModuleIndex.Source(
        repositoryUri, text(value.get("license"), path + ".license"));
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

  private SourceModuleIndex.Build build(Map<String, Object> value, String path) {
    return new SourceModuleIndex.Build(
        stringList(value.get("command"), path + ".command", true),
        stringList(value.get("verificationCommand"), path + ".verificationCommand", true));
  }

  private List<SourceModuleIndex.Factory> factories(Object value, String path) {
    Set<String> factoryIds = new HashSet<>();
    List<Object> factories = list(value, path, true);
    List<SourceModuleIndex.Factory> parsedFactories = new ArrayList<>();
    for (int index = 0; index < factories.size(); index++) {
      String factoryPath = path + "[" + index + "]";
      Map<String, Object> factory = map(factories.get(index), factoryPath);
      String factoryId = text(factory.get("factoryId"), factoryPath + ".factoryId");
      requireUnique(factoryIds, factoryId, factoryPath + ".factoryId");
      parsedFactories.add(
          new SourceModuleIndex.Factory(
              factoryId, text(factory.get("providerType"), factoryPath + ".providerType")));
    }
    return parsedFactories;
  }

  private List<SourceModuleIndex.Operation> operations(Object value, String path) {
    Set<String> operationNames = new HashSet<>();
    List<Object> operations = list(value, path, true);
    List<SourceModuleIndex.Operation> parsedOperations = new ArrayList<>();
    for (int index = 0; index < operations.size(); index++) {
      String operationPath = path + "[" + index + "]";
      Map<String, Object> operation = map(operations.get(index), operationPath);
      String name = text(operation.get("name"), operationPath + ".name");
      requireUnique(operationNames, name, operationPath + ".name");
      parsedOperations.add(
          new SourceModuleIndex.Operation(
              name,
              text(operation.get("description"), operationPath + ".description"),
              bool(operation.get("sideEffecting"), operationPath + ".sideEffecting"),
              map(operation.get("inputSchema"), operationPath + ".inputSchema")));
    }
    return parsedOperations;
  }

  private SourceModuleIndex.Security security(Map<String, Object> value, String path) {
    return new SourceModuleIndex.Security(
        stringList(value.get("permissions"), path + ".permissions", false));
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

  private static List<String> stringList(Object value, String path, boolean requireNonEmpty) {
    List<Object> values = list(value, path, requireNonEmpty);
    List<String> strings = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      strings.add(text(values.get(index), path + "[" + index + "]"));
    }
    return strings;
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

  private static boolean bool(Object value, String path) {
    if (!(value instanceof Boolean bool)) {
      throw invalid(path, "must be a boolean");
    }
    return bool;
  }

  private static void requireUnique(Set<String> values, String value, String path) {
    if (!values.add(value)) {
      throw invalid(path, "must be unique");
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
