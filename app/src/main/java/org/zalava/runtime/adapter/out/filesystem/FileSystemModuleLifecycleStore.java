package org.zalava.runtime.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** Host-owned desired module state, stored beside the enabled-module registry. */
public final class FileSystemModuleLifecycleStore {
  public enum DesiredState {
    RUNNING,
    STOPPED
  }

  private final Path file;

  public FileSystemModuleLifecycleStore(Path workspace) {
    this.file =
        workspace
            .toAbsolutePath()
            .normalize()
            .resolve("source-module-installation/module-lifecycle.properties");
  }

  public synchronized Optional<DesiredState> desired(String moduleId) {
    String value = read().getProperty(moduleId);
    return value == null ? Optional.empty() : Optional.of(DesiredState.valueOf(value));
  }

  public synchronized void set(String moduleId, DesiredState state) {
    Properties values = read();
    values.setProperty(moduleId, state.name());
    write(values);
  }

  public synchronized void registerNewInstall(String moduleId) {
    Properties values = read();
    if (values.containsKey(moduleId)) return;
    values.setProperty(moduleId, DesiredState.STOPPED.name());
    write(values);
  }

  public synchronized void migrateExisting(List<String> enabledModuleIds) {
    Properties values = read();
    boolean changed = false;
    for (String moduleId : enabledModuleIds) {
      if (!values.containsKey(moduleId)) {
        values.setProperty(moduleId, DesiredState.RUNNING.name());
        changed = true;
      }
    }
    if (changed) write(values);
  }

  private Properties read() {
    Properties values = new Properties();
    if (!Files.isRegularFile(file)) return values;
    try (InputStream input = Files.newInputStream(file)) {
      values.load(input);
      return values;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read module lifecycle state", exception);
    }
  }

  private void write(Properties values) {
    try {
      Files.createDirectories(file.getParent());
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      try (OutputStream output = Files.newOutputStream(temporary)) {
        values.store(output, "SEA module lifecycle");
      }
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to persist module lifecycle state", exception);
    }
  }
}
