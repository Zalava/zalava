package org.zalava.modules.runtime.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.zalava.modules.runtime.application.port.in.ManagedSeaRestart;

/** Persists a narrow host-manager restart signal; it never executes host commands in SEA. */
public final class FileSystemManagedSeaRestart implements ManagedSeaRestart {
  private static final String REQUEST_ID = "requestId";
  private static final String PHASE = "phase";
  private static final String REQUESTED_AT = "requestedAt";
  private final Path file;
  private final Path failureFile;
  private final Clock clock;
  private final boolean available;

  public FileSystemManagedSeaRestart(Path workspace, Clock clock, boolean available) {
    this.file =
        workspace.toAbsolutePath().normalize().resolve("sea-runtime-restart/request.properties");
    this.failureFile = file.resolveSibling("failure");
    this.clock = clock;
    this.available = available;
    completePriorRequest();
  }

  @Override
  public synchronized Status status() {
    if (!available) {
      return Status.unavailable(
          "SEA restart is unavailable because the local service-manager dispatcher is not installed.");
    }
    Properties values = read();
    if (values.isEmpty()) {
      return new Status(Availability.AVAILABLE, Phase.IDLE, null, "Ready to restart SEA.");
    }
    if (Files.isRegularFile(failureFile)) {
      return new Status(
          Availability.AVAILABLE,
          Phase.FAILED,
          Instant.parse(values.getProperty(REQUESTED_AT)),
          message(Phase.FAILED));
    }
    return new Status(
        Availability.AVAILABLE,
        Phase.valueOf(values.getProperty(PHASE)),
        Instant.parse(values.getProperty(REQUESTED_AT)),
        message(Phase.valueOf(values.getProperty(PHASE))));
  }

  @Override
  public synchronized Status request() {
    Status current = status();
    if (current.availability() == Availability.UNAVAILABLE || current.phase() == Phase.REQUESTED) {
      return current;
    }
    Instant requestedAt = clock.instant();
    deleteFailure();
    Properties values = new Properties();
    values.setProperty(REQUEST_ID, UUID.randomUUID().toString());
    values.setProperty(PHASE, Phase.REQUESTED.name());
    values.setProperty(REQUESTED_AT, requestedAt.toString());
    write(values);
    return new Status(
        Availability.AVAILABLE,
        Phase.REQUESTED,
        requestedAt,
        "Restart requested. SEA will briefly disconnect while the local service manager starts it again.");
  }

  private void completePriorRequest() {
    if (!available) {
      return;
    }
    Properties values = read();
    if (Phase.REQUESTED.name().equals(values.getProperty(PHASE))) {
      values.setProperty(PHASE, Phase.COMPLETED.name());
      write(values);
    }
  }

  private Properties read() {
    Properties values = new Properties();
    if (!Files.isRegularFile(file)) {
      return values;
    }
    try (InputStream input = Files.newInputStream(file)) {
      values.load(input);
      return values;
    } catch (IOException | IllegalArgumentException exception) {
      throw new IllegalStateException("Unable to read SEA restart status", exception);
    }
  }

  private void write(Properties values) {
    try {
      Files.createDirectories(file.getParent());
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      try (OutputStream output = Files.newOutputStream(temporary)) {
        values.store(output, "SEA managed restart");
      }
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to persist SEA restart status", exception);
    }
  }

  private void deleteFailure() {
    try {
      Files.deleteIfExists(failureFile);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to prepare SEA restart request", exception);
    }
  }

  private static String message(Phase phase) {
    return switch (phase) {
      case IDLE -> "Ready to restart SEA.";
      case REQUESTED ->
          "Restart requested. SEA will briefly disconnect while the local service manager starts it again.";
      case COMPLETED -> "SEA restarted and can now load installed modules.";
      case FAILED -> "SEA restart failed. Check the SEA logs for service-manager diagnostics.";
    };
  }
}
