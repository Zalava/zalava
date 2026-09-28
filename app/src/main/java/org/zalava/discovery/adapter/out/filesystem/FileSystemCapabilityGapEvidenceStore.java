package org.zalava.discovery.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.discovery.CapabilityGapEvidence;
import org.zalava.discovery.application.port.out.CapabilityGapEvidenceStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Atomic, bounded filesystem adapter for actor-safe capability-gap evidence. */
public final class FileSystemCapabilityGapEvidenceStore implements CapabilityGapEvidenceStore {

  public static final int MAX_RECORDS = 200;

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<List<CapabilityGapEvidence>> TYPE = new TypeReference<>() {};
  private final Path file;

  public FileSystemCapabilityGapEvidenceStore(Path workspace) {
    this.file = workspace.resolve("discovery/capability-gap-evidence.json");
  }

  @Override
  public synchronized void save(CapabilityGapEvidence evidence) {
    List<CapabilityGapEvidence> values = new ArrayList<>(read());
    values.add(evidence);
    while (values.size() > MAX_RECORDS) {
      values.removeFirst();
    }
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.getParent());
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), values);
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to persist capability gap evidence", exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public synchronized List<CapabilityGapEvidence> recent(int limit) {
    List<CapabilityGapEvidence> values = read();
    int fromIndex = Math.max(0, values.size() - limit);
    List<CapabilityGapEvidence> recent = new ArrayList<>(values.subList(fromIndex, values.size()));
    java.util.Collections.reverse(recent);
    return List.copyOf(recent);
  }

  private List<CapabilityGapEvidence> read() {
    if (!Files.isRegularFile(file)) {
      return new ArrayList<>();
    }
    try {
      return new ArrayList<>(JSON.readValue(file.toFile(), TYPE));
    } catch (RuntimeException exception) {
      throw new SourceModuleInstallationException(
          "Unable to read capability gap evidence", exception);
    }
  }
}
