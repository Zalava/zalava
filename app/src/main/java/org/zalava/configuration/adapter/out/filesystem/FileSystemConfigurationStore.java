package org.zalava.configuration.adapter.out.filesystem;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.zalava.configuration.application.port.out.ConfigurationStore;

public final class FileSystemConfigurationStore implements ConfigurationStore {

  private final Path configurationPath;

  public FileSystemConfigurationStore(Path configurationPath) {
    this.configurationPath = configurationPath;
  }

  public static FileSystemConfigurationStore fromLocation(String location) {
    return new FileSystemConfigurationStore(pathFromLocation(location));
  }

  /** Resolves the private application configuration file used by all local configuration stores. */
  public static Path pathFromLocation(String location) {
    if (location == null || location.isBlank()) {
      return Path.of("app", "src", "main", "resources", "application.private.yaml");
    }
    String candidate = location.split(",")[0].trim().replace("file:", "");
    Path path = Path.of(candidate);
    return Files.isDirectory(path) || candidate.endsWith("/")
        ? path.resolve("application.private.yaml")
        : path;
  }

  /** Resolves the first filesystem-backed configuration import, if one was supplied. */
  public static Path pathFromImport(String imports) {
    if (imports == null || imports.isBlank()) {
      return pathFromLocation(null);
    }
    return Arrays.stream(imports.split(","))
        .map(String::trim)
        .map(value -> value.replaceFirst("^optional:", ""))
        .filter(value -> value.startsWith("file:"))
        .map(value -> pathFromLocation(value.substring("file:".length())))
        .findFirst()
        .orElseGet(() -> pathFromLocation(null));
  }

  @Override
  public Map<String, Object> read() throws IOException {
    try (FileReader input = new FileReader(configurationPath.toFile())) {
      Map<String, Object> configuration = new Yaml().load(input);
      return configuration == null ? new LinkedHashMap<>() : configuration;
    } catch (FileNotFoundException exception) {
      return new LinkedHashMap<>();
    }
  }

  @Override
  public void write(Map<String, Object> configuration) throws IOException {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    Files.createDirectories(configurationPath.getParent());
    try (FileWriter writer = new FileWriter(configurationPath.toFile())) {
      new Yaml(options).dump(configuration, writer);
    }
  }
}
