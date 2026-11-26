package org.zalava.assistant.chat.adapter.out.filesystem;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.platform.storage.private_state.ActorScopedPaths;
import tools.jackson.databind.ObjectMapper;

/** Actor-partitioned, bounded records; files contain only the validated evidence fields. */
@Component
public final class FileSystemStandingHomeActionEvidenceStore
    implements StandingHomeActionEvidenceStore {
  private static final Logger LOG =
      LoggerFactory.getLogger(FileSystemStandingHomeActionEvidenceStore.class);
  static final int MAX_ENTRIES = 200;
  private static final int MAX_BYTES = 4096;
  private static final String AREA = "home-action-evidence";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ActorScopedPaths paths;

  @Autowired
  public FileSystemStandingHomeActionEvidenceStore(
      @Value("${agent.workspace:Unknown}") Resource workspace) throws IOException {
    this(workspace.getFilePath());
  }

  public FileSystemStandingHomeActionEvidenceStore(Path workspace) {
    paths = new ActorScopedPaths(workspace);
  }

  @Override
  public synchronized void record(Actor actor, Evidence evidence) {
    requireOwner(actor, evidence.conversationId());
    Path directory = paths.directory(actor, AREA);
    Path temporary = null;
    try (FileChannel channel = openLock(actor);
        FileLock lock = channel.tryLock()) {
      if (lock == null) throw new IllegalStateException("Home Assistant evidence is busy");
      String suffix = "-" + evidence.executionId() + ".json";
      try (Stream<Path> files = Files.list(directory)) {
        if (files
            .filter(FileSystemStandingHomeActionEvidenceStore::recordFile)
            .anyMatch(file -> file.getFileName().toString().endsWith(suffix))) return;
      }
      temporary = Files.createTempFile(directory, ".home-action-", ".tmp");
      JSON.writeValue(temporary.toFile(), evidence);
      Instant at = Instant.parse(evidence.timestamp());
      Path target =
          paths.file(
              actor,
              AREA,
              String.format(
                  Locale.ROOT,
                  "%019d-%09d-%s.json",
                  at.getEpochSecond(),
                  at.getNano(),
                  evidence.executionId()));
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      try (Stream<Path> files = Files.list(directory)) {
        for (Path expired :
            files
                .filter(FileSystemStandingHomeActionEvidenceStore::recordFile)
                .sorted(
                    Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .skip(MAX_ENTRIES)
                .toList()) {
          Files.deleteIfExists(expired);
        }
      }
    } catch (IOException | RuntimeException failure) {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // Preserve the original failure.
        }
      }
      throw new IllegalStateException("Unable to retain Home Assistant action evidence", failure);
    }
  }

  @Override
  public synchronized List<Evidence> recent(Actor actor, String conversationId) {
    requireOwner(actor, conversationId);
    Path directory = paths.directory(actor, AREA);
    try (FileChannel channel = openLock(actor);
        FileLock lock = channel.tryLock();
        Stream<Path> files = Files.list(directory)) {
      if (lock == null) throw new IllegalStateException("Home Assistant evidence is busy");
      return files
          .filter(FileSystemStandingHomeActionEvidenceStore::recordFile)
          .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
          .limit(MAX_ENTRIES)
          .map(this::read)
          .filter(Objects::nonNull)
          .filter(evidence -> conversationId.equals(evidence.conversationId()))
          .toList();
    } catch (IOException | RuntimeException failure) {
      throw new IllegalStateException("Unable to read Home Assistant action evidence", failure);
    }
  }

  private FileChannel openLock(Actor actor) throws IOException {
    return FileChannel.open(
        paths.file(actor, AREA, ".lock"),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        LinkOption.NOFOLLOW_LINKS);
  }

  private Evidence read(Path file) {
    try {
      if (Files.size(file) > MAX_BYTES) {
        LOG.warn("Skipping oversized Home Assistant action evidence");
        return null;
      }
      return JSON.readValue(file.toFile(), Evidence.class);
    } catch (IOException | RuntimeException invalid) {
      LOG.warn("Skipping invalid Home Assistant action evidence");
      return null;
    }
  }

  private static boolean recordFile(Path file) {
    return file.getFileName().toString().matches("[0-9]{19}-[0-9]{9}-[a-f0-9-]{36}\\.json")
        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
  }

  private static void requireOwner(Actor actor, String conversationId) {
    if (!ActorConversationId.parse(conversationId).actor().equals(actor))
      throw new IllegalArgumentException("Home Assistant evidence belongs to another actor");
  }
}
