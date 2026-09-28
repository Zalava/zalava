package org.zalava.managed.adapter.out.filesystem;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.zalava.managed.application.ManagedServiceRecord;
import org.zalava.managed.application.port.out.ManagedServiceBackupPort;

/**
 * Filesystem backups for SEA-managed services: every declared data path is archived into its own
 * zip inside one timestamped {@code managed-backups/<service-id>/...} directory, so multi-path
 * services restore each path's contents under exactly that path. Backups only cover the declared
 * data paths of the service record; a service that declares none has nothing to preserve and fails
 * loudly instead of pretending to be backed up.
 */
public final class FileSystemManagedServiceBackupAdapter implements ManagedServiceBackupPort {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final Path root;

  public FileSystemManagedServiceBackupAdapter(Path workspaceRoot) {
    try {
      this.root = workspaceRoot.resolve("managed-backups");
      Files.createDirectories(root);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create managed-service backup directory", ex);
    }
  }

  @Override
  public BackupArtifact execute(ManagedServiceRecord record) {
    Set<String> dataPaths = new TreeSet<>(record.desiredState().dataPaths());
    if (dataPaths.isEmpty()) {
      throw new IllegalStateException(
          "Service '"
              + record.serviceId()
              + "' declares no data paths; there is nothing to back up, so a rollback could not "
              + "restore authoritative data");
    }
    Path backupDirectory =
        root.resolve(record.serviceId())
            .resolve(record.desiredState().revision() + "-" + TIMESTAMP.format(Instant.now()));
    try {
      Files.createDirectories(backupDirectory);
      for (String dataPath : dataPaths) {
        archivePath(Path.of(dataPath), archiveFor(backupDirectory, dataPath));
      }
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to write backup for '" + record.serviceId() + "'", ex);
    }
    return new BackupArtifact(record.serviceId(), Instant.now(), backupDirectory.toString());
  }

  @Override
  public void restore(ManagedServiceRecord record, String backupLocation) {
    Set<String> dataPaths = new TreeSet<>(record.desiredState().dataPaths());
    Path backupDirectory = Path.of(backupLocation);
    if (!Files.isDirectory(backupDirectory)) {
      throw new IllegalStateException(
          "Backup directory for '" + record.serviceId() + "' is missing: " + backupLocation);
    }
    try {
      for (String dataPath : dataPaths) {
        Path archive = archiveFor(backupDirectory, dataPath);
        if (!Files.isRegularFile(archive)) {
          throw new IOException("Backup archive missing for data path " + dataPath);
        }
        restoreArchive(archive, Path.of(dataPath));
      }
    } catch (IOException ex) {
      throw new IllegalStateException(
          "Unable to restore backup for '" + record.serviceId() + "'", ex);
    }
  }

  /** Filesystem-safe, collision-free per-path archive name (URL-safe base64 of the path). */
  private static Path archiveFor(Path backupDirectory, String dataPath) {
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(dataPath.getBytes(StandardCharsets.UTF_8));
    return backupDirectory.resolve(encoded + ".zip");
  }

  private static void archivePath(Path path, Path archive) throws IOException {
    if (!Files.isDirectory(path)) {
      return; // Nothing on disk yet for this declared path; nothing to archive.
    }
    try (OutputStream output = Files.newOutputStream(archive);
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      archiveDirectory(zip, path, path);
    }
  }

  private static void archiveDirectory(ZipOutputStream zip, Path rootPath, Path current)
      throws IOException {
    try (var paths = Files.list(current)) {
      for (Path entry : paths.sorted(Comparator.naturalOrder()).toList()) {
        String entryName = rootPath.relativize(entry).toString();
        if (Files.isDirectory(entry)) {
          zip.putNextEntry(new ZipEntry(entryName + "/"));
          zip.closeEntry();
          archiveDirectory(zip, rootPath, entry);
        } else {
          zip.putNextEntry(new ZipEntry(entryName));
          Files.copy(entry, zip);
          zip.closeEntry();
        }
      }
    }
  }

  private static void restoreArchive(Path archive, Path targetRoot) throws IOException {
    Files.createDirectories(targetRoot);
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        Path target = targetRoot.resolve(entry.getName()).normalize();
        if (!target.startsWith(targetRoot)) {
          throw new IOException("Backup entry escapes its data path: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(zip.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
