package org.zalava.catalog;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Parses the module metadata produced alongside a locally developed SEA artifact. */
public final class LocalArtifactModuleMetadataLoader {

  private static final int SUPPORTED_SCHEMA_VERSION = 1;
  private static final String RETIRED_SEA_MODULE_SPI =
      "META-INF/services/org.zalava.sea.ZalavaModule";

  /** Reads module-owned metadata from an uploaded JAR without extracting it. */
  public SourceModuleIndex loadJar(Path artifact) {
    try (JarFile jar = new JarFile(artifact.toFile())) {
      if (jar.getJarEntry(RETIRED_SEA_MODULE_SPI) != null) {
        throw invalid("uploaded JAR", "must not use the retired ZalavaModule SPI descriptor");
      }
      var entry = jar.getJarEntry("module-metadata.yaml");
      if (entry == null || entry.isDirectory()) {
        throw invalid("module-metadata.yaml", "must be embedded in the uploaded JAR");
      }
      try (var input = jar.getInputStream(entry)) {
        return load(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (IOException exception) {
      throw invalid("uploaded JAR", "must be a readable JAR", exception);
    }
  }

  public SourceModuleIndex load(String yaml) {
    Object document;
    try {
      document = new Yaml(new SafeConstructor(new LoaderOptions())).load(new StringReader(yaml));
    } catch (YAMLException exception) {
      throw invalid("metadata", "must be valid YAML", exception);
    }
    Map<String, Object> root = map(document, "metadata");
    if (integer(root.get("schemaVersion"), "schemaVersion") != SUPPORTED_SCHEMA_VERSION) {
      throw invalid("schemaVersion", "must be " + SUPPORTED_SCHEMA_VERSION);
    }
    List<Object> modules = list(root.get("modules"), "modules", true);
    Set<String> moduleIds = new HashSet<>();
    List<SourceModuleIndex.Module> parsed = new ArrayList<>();
    for (int index = 0; index < modules.size(); index++) {
      String path = "modules[" + index + "]";
      SourceModuleIndex.Module module = module(map(modules.get(index), path), path);
      if (!moduleIds.add(module.moduleId())) {
        throw invalid(path + ".moduleId", "must be unique");
      }
      parsed.add(module);
    }
    return new SourceModuleIndex(SUPPORTED_SCHEMA_VERSION, parsed);
  }

  private SourceModuleIndex.Module module(Map<String, Object> value, String path) {
    String version = text(value.get("version"), path + ".version");
    Map<String, Object> artifact = map(value.get("artifact"), path + ".artifact");
    String artifactVersion = text(artifact.get("version"), path + ".artifact.version");
    if (!version.equals(artifactVersion)) {
      throw invalid(path + ".artifact.version", "must match module version");
    }
    return new SourceModuleIndex.Module(
        text(value.get("moduleId"), path + ".moduleId"),
        version,
        text(value.get("displayName"), path + ".displayName"),
        text(value.get("description"), path + ".description"),
        httpsUri(value.get("supportUrl"), path + ".supportUrl"),
        new SourceModuleIndex.Artifact(
            text(artifact.get("groupId"), path + ".artifact.groupId"),
            text(artifact.get("artifactId"), path + ".artifact.artifactId"),
            artifactVersion),
        null,
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(
            text(
                map(value.get("compatibility"), path + ".compatibility").get("seaRuntime"),
                path + ".compatibility.seaRuntime")),
        map(value.get("configurationSchema"), path + ".configurationSchema"),
        factories(value.get("factories"), path + ".factories"),
        operations(value.get("operations"), path + ".operations"),
        new SourceModuleIndex.Security(
            strings(
                map(value.get("security"), path + ".security").get("permissions"),
                path + ".security.permissions",
                false)));
  }

  private List<SourceModuleIndex.Factory> factories(Object value, String path) {
    List<SourceModuleIndex.Factory> factories = new ArrayList<>();
    for (Object item : list(value, path, true)) {
      Map<String, Object> factory = map(item, path + "[]");
      factories.add(
          new SourceModuleIndex.Factory(
              text(factory.get("factoryId"), path + "[].factoryId"),
              text(factory.get("providerType"), path + "[].providerType")));
    }
    return factories;
  }

  private List<SourceModuleIndex.Operation> operations(Object value, String path) {
    List<SourceModuleIndex.Operation> operations = new ArrayList<>();
    for (Object item : list(value, path, false)) {
      Map<String, Object> operation = map(item, path + "[]");
      String name = text(operation.get("name"), path + "[].name");
      Object description = operation.get("description");
      operations.add(
          new SourceModuleIndex.Operation(
              name,
              description instanceof String string && !string.isBlank() ? string : name,
              operation.get("sideEffecting") instanceof Boolean sideEffecting && sideEffecting,
              map(operation.get("inputSchema"), path + "[].inputSchema")));
    }
    return operations;
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

  private static List<String> strings(Object value, String path, boolean nonEmpty) {
    List<String> result = new ArrayList<>();
    for (Object item : list(value, path, nonEmpty)) result.add(text(item, path + "[]"));
    return result;
  }

  private static int integer(Object value, String path) {
    if (!(value instanceof Integer number)) throw invalid(path, "must be an integer");
    return number;
  }

  private static String text(Object value, String path) {
    if (!(value instanceof String string) || string.isBlank())
      throw invalid(path, "must be a non-blank string");
    return string;
  }

  private static SourceModuleIndexValidationException invalid(String path, String message) {
    return new SourceModuleIndexValidationException(path + " " + message);
  }

  private static SourceModuleIndexValidationException invalid(
      String path, String message, Throwable cause) {
    return new SourceModuleIndexValidationException(path + " " + message, cause);
  }
}
