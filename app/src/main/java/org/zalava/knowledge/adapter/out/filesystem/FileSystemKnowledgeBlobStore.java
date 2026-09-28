package org.zalava.knowledge.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.zalava.knowledge.application.port.out.KnowledgeBlobStore;
import org.zalava.knowledge.domain.KnowledgeSourceId;

/** Controlled local storage for original knowledge bytes, with one independent blob per source. */
public final class FileSystemKnowledgeBlobStore implements KnowledgeBlobStore {
  private final Path originals;

  public FileSystemKnowledgeBlobStore(Path managedDataRoot) {
    this.originals = managedDataRoot.toAbsolutePath().normalize().resolve("knowledge/originals");
    try {
      Files.createDirectories(originals);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to create managed knowledge storage", ex);
    }
  }

  @Override
  public synchronized BlobReceipt write(KnowledgeSourceId sourceId, byte[] content) {
    if (content == null) throw new IllegalArgumentException("content must not be null");
    Path target = path(sourceId);
    Path temporary;
    try {
      temporary = Files.createTempFile(originals, "." + sourceId.value() + "-", ".pending");
      Files.write(temporary, content);
      move(temporary, target);
      return new BlobReceipt(content.length, sha256(content));
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to store source original", ex);
    }
  }

  @Override
  public synchronized Optional<byte[]> read(KnowledgeSourceId sourceId) {
    try {
      Path path = path(sourceId);
      return Files.isRegularFile(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read source original", ex);
    }
  }

  @Override
  public synchronized void delete(KnowledgeSourceId sourceId) {
    try {
      Files.deleteIfExists(path(sourceId));
      try (var entries = Files.list(originals)) {
        entries
            .filter(path -> path.getFileName().toString().startsWith("." + sourceId.value() + "-"))
            .forEach(this::deleteQuietly);
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to delete source original", ex);
    }
  }

  private Path path(KnowledgeSourceId sourceId) {
    if (sourceId == null) throw new IllegalArgumentException("sourceId must not be null");
    return originals.resolve(sourceId.value() + ".blob");
  }

  private static void move(Path temporary, Path target) throws IOException {
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to remove incomplete source write", ex);
    }
  }
}
