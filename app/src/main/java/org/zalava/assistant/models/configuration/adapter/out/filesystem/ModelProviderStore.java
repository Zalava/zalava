package org.zalava.assistant.models.configuration.adapter.out.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.zalava.platform.configuration.application.port.out.ConfigurationStore;

/** Provider credentials live in writable workspace storage, outside read-only host config. */
public final class ModelProviderStore implements ConfigurationStore {
  public static final String FILE_NAME = "MODEL-PROVIDER.private.yaml";
  private final Path path;

  public ModelProviderStore(Path workspace) {
    Path normalized = workspace.toAbsolutePath().normalize();
    path = normalized.resolveSibling(normalized.getFileName() + "-model-config").resolve(FILE_NAME);
  }

  public Path path() {
    return path;
  }

  @Override
  public Map<String, Object> read() throws IOException {
    if (Files.isSymbolicLink(path.getParent()) || Files.isSymbolicLink(path))
      throw new IOException("Provider storage cannot be a symbolic link.");
    if (!Files.exists(path)) return new LinkedHashMap<>();
    try (var reader = Files.newBufferedReader(path)) {
      Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
      if (document == null) return new LinkedHashMap<>();
      if (!(document instanceof Map<?, ?> map))
        throw new IOException("Saved model provider settings are invalid.");
      var values = new LinkedHashMap<String, Object>();
      for (var entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key))
          throw new IOException("Saved model provider settings are invalid.");
        values.put(key, entry.getValue());
      }
      return values;
    } catch (YAMLException invalid) {
      // YAML parser diagnostics can contain the private input: do not retain the cause.
      throw new IOException("Saved model provider settings are invalid.");
    }
  }

  @Override
  public void write(Map<String, Object> configuration) throws IOException {
    if (Files.isSymbolicLink(path.getParent()) || Files.isSymbolicLink(path))
      throw new IOException("Provider storage cannot be a symbolic link.");
    Files.createDirectories(
        path.getParent(),
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    Files.setPosixFilePermissions(path.getParent(), PosixFilePermissions.fromString("rwx------"));
    Path temporary =
        Files.createTempFile(
            path.getParent(),
            ".model-provider-",
            ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try {
      Files.writeString(temporary, new Yaml().dump(configuration), StandardCharsets.UTF_8);
      Files.move(
          temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
