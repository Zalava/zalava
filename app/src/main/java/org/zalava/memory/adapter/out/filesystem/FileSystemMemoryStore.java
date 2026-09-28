package org.zalava.memory.adapter.out.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.zalava.accounts.domain.Actor;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.memory.application.port.out.ActorMemoryStore;
import org.zalava.memory.application.port.out.MemoryStore;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;
import org.zalava.private_state.ActorScopedPaths;

/**
 * Filesystem-backed durable memory. Actor records live under {@code
 * users/&lt;account-id&gt;/memory} and remain private to the owning actor; the legacy global {@code
 * memory} directory is retained for backward compatibility with pre-ownership records.
 *
 * <p>The persisted YAML contract is additive: records written before provenance existed load with
 * {@link MemoryProvenance#legacy()}, and records with an unknown scope or missing required field
 * are skipped rather than failing a listing.
 */
public final class FileSystemMemoryStore implements MemoryStore, ActorMemoryStore {

  private static final String EXTENSION = ".yaml";
  private static final String PROVENANCE_SOURCE = "provenance.source";
  private static final String PROVENANCE_REFERENCE = "provenance.reference";
  private final Path memoryDirectory;
  private final ActorScopedPaths actorPaths;

  public FileSystemMemoryStore(Path workspaceDirectory) {
    this.memoryDirectory = workspaceDirectory.resolve("memory");
    this.actorPaths = new ActorScopedPaths(workspaceDirectory);
  }

  @Override
  public synchronized Memory remember(MemoryDraft draft) {
    Memory memory = newMemory(draft);
    try {
      Files.createDirectories(memoryDirectory);
      Files.writeString(
          memoryDirectory.resolve(fileName(memory)),
          YamlParser.serialize(new YamlDocument(frontmatter(memory), null)),
          StandardOpenOption.CREATE_NEW);
      return memory;
    } catch (IOException exception) {
      throw new RuntimeException("Failed to save memory: " + memory.id(), exception);
    }
  }

  @Override
  public synchronized List<Memory> recent(int limit) {
    validateLimit(limit);
    return ordered(all(memoryDirectory)).stream().limit(limit).toList();
  }

  @Override
  public synchronized List<Memory> search(String query, int limit) {
    validateLimit(limit);
    String normalized = normalizeQuery(query);
    if (normalized == null) return List.of();
    return ordered(all(memoryDirectory)).stream()
        .filter(memory -> matches(memory, normalized))
        .limit(limit)
        .toList();
  }

  @Override
  public synchronized Memory remember(Actor actor, MemoryDraft draft) {
    Memory memory = newMemory(draft);
    write(
        actorPaths.file(actor, "memory", fileName(memory)), memory, StandardOpenOption.CREATE_NEW);
    return memory;
  }

  @Override
  public synchronized List<Memory> recent(Actor actor, Set<MemoryScope> scopes, int limit) {
    validateLimit(limit);
    Set<MemoryScope> filters = requireScopes(scopes);
    return ordered(owned(actor)).stream()
        .filter(memory -> filters.contains(memory.scope()))
        .limit(limit)
        .toList();
  }

  @Override
  public synchronized List<Memory> search(
      Actor actor, Set<MemoryScope> scopes, String query, int limit) {
    validateLimit(limit);
    Set<MemoryScope> filters = requireScopes(scopes);
    String normalized = normalizeQuery(query);
    if (normalized == null) return List.of();
    return ordered(owned(actor)).stream()
        .filter(memory -> filters.contains(memory.scope()))
        .filter(memory -> matches(memory, normalized))
        .limit(limit)
        .toList();
  }

  @Override
  public synchronized Optional<Memory> find(Actor actor, String memoryId) {
    Objects.requireNonNull(memoryId, "memoryId must not be null");
    return owned(actor).stream().filter(memory -> memory.id().equals(memoryId)).findFirst();
  }

  @Override
  public synchronized boolean delete(Actor actor, String memoryId) {
    Objects.requireNonNull(memoryId, "memoryId must not be null");
    Path directory = actorPaths.directory(actor, "memory");
    for (Path file : files(directory)) {
      Optional<Memory> memory = read(file);
      if (memory.isPresent() && memory.get().id().equals(memoryId)) {
        return deleteFile(file);
      }
    }
    return false;
  }

  @Override
  public synchronized Optional<Memory> update(
      Actor actor, String memoryId, MemoryScope scope, String text) {
    Objects.requireNonNull(scope, "scope must not be null");
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(memoryId, "memoryId must not be null");
    Path directory = actorPaths.directory(actor, "memory");
    for (Path file : files(directory)) {
      Optional<Memory> existing = read(file);
      if (existing.isPresent() && existing.get().id().equals(memoryId)) {
        Memory updated = existing.get().revised(scope, text, Instant.now());
        overwrite(file, updated);
        return Optional.of(updated);
      }
    }
    return Optional.empty();
  }

  @Override
  public synchronized int delete(Actor actor, Set<MemoryScope> scopes) {
    Set<MemoryScope> filters = requireScopes(scopes);
    if (filters.isEmpty()) return 0;
    Path directory = actorPaths.directory(actor, "memory");
    int deleted = 0;
    for (Path file : files(directory)) {
      Optional<Memory> memory = read(file);
      if (memory.isPresent() && filters.contains(memory.get().scope()) && deleteFile(file)) {
        deleted++;
      }
    }
    return deleted;
  }

  private List<Memory> owned(Actor actor) {
    Objects.requireNonNull(actor, "actor must not be null");
    return all(actorPaths.directory(actor, "memory"));
  }

  private static List<Memory> all(Path directory) {
    if (!Files.exists(directory)) return List.of();
    return files(directory).stream()
        .map(FileSystemMemoryStore::read)
        .flatMap(Optional::stream)
        .toList();
  }

  private static List<Path> files(Path directory) {
    if (!Files.exists(directory)) return List.of();
    try (Stream<Path> paths = Files.list(directory)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
          .toList();
    } catch (IOException exception) {
      throw new RuntimeException("Failed to list memories", exception);
    }
  }

  private static List<Memory> ordered(List<Memory> memories) {
    return memories.stream()
        .sorted(Comparator.comparing(Memory::createdAt).reversed().thenComparing(Memory::id))
        .toList();
  }

  private static Memory newMemory(MemoryDraft draft) {
    return new Memory(
        UUID.randomUUID().toString(),
        draft.scope(),
        draft.text(),
        draft.metadata(),
        Instant.now(),
        draft.provenance());
  }

  private static void write(Path file, Memory memory, StandardOpenOption option) {
    try {
      Files.writeString(
          file, YamlParser.serialize(new YamlDocument(frontmatter(memory), null)), option);
    } catch (IOException exception) {
      throw new RuntimeException("Failed to save memory: " + memory.id(), exception);
    }
  }

  private static void overwrite(Path file, Memory memory) {
    write(file, memory, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static boolean deleteFile(Path file) {
    try {
      return Files.deleteIfExists(file);
    } catch (IOException exception) {
      throw new RuntimeException("Failed to delete memory file: " + file, exception);
    }
  }

  private static Optional<Memory> read(Path file) {
    try {
      Map<String, String> frontmatter = YamlParser.parse(Files.readString(file)).frontmatter();
      return Optional.of(
          new Memory(
              required(frontmatter, "id"),
              MemoryScope.parse(required(frontmatter, "scope")),
              decode(required(frontmatter, "text")),
              metadata(frontmatter),
              Instant.parse(required(frontmatter, "createdAt")),
              provenance(frontmatter),
              optionalInstant(frontmatter, "updatedAt")));
    } catch (RuntimeException | IOException exception) {
      return Optional.empty();
    }
  }

  private static Map<String, String> frontmatter(Memory memory) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("id", memory.id());
    result.put("scope", memory.scope().name());
    result.put("createdAt", memory.createdAt().toString());
    if (memory.updatedAt() != null) {
      result.put("updatedAt", memory.updatedAt().toString());
    }
    result.put("text", encode(memory.text()));
    if (memory.provenance().reference() != null) {
      result.put(PROVENANCE_REFERENCE, encode(memory.provenance().reference()));
    }
    result.put(PROVENANCE_SOURCE, encode(memory.provenance().source()));
    memory.metadata().forEach((key, value) -> result.put("metadata." + key, encode(value)));
    return result;
  }

  private static MemoryProvenance provenance(Map<String, String> values) {
    String source = values.get(PROVENANCE_SOURCE);
    if (source == null || source.isBlank()) {
      return MemoryProvenance.legacy();
    }
    String reference = values.get(PROVENANCE_REFERENCE);
    return new MemoryProvenance(
        decode(source), reference == null || reference.isBlank() ? null : decode(reference));
  }

  private static Map<String, String> metadata(Map<String, String> values) {
    Map<String, String> result = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> {
          if (key.startsWith("metadata.")) result.put(key.substring(9), decode(value));
        });
    return result;
  }

  private static boolean matches(Memory memory, String normalizedQuery) {
    return memory.text().toLowerCase().contains(normalizedQuery)
        || memory.scope().name().toLowerCase().contains(normalizedQuery)
        || memory.provenance().source().toLowerCase().contains(normalizedQuery)
        || memory.metadata().values().stream()
            .anyMatch(value -> value.toLowerCase().contains(normalizedQuery));
  }

  private static String fileName(Memory memory) {
    return memory.createdAt().toString().replace(":", "").replace(".", "-")
        + "-"
        + memory.id().replaceAll("[^a-zA-Z0-9._-]", "_")
        + EXTENSION;
  }

  private static String required(Map<String, String> values, String key) {
    String value = values.get(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing memory field: " + key);
    return value;
  }

  private static Instant optionalInstant(Map<String, String> values, String key) {
    String value = values.get(key);
    return value == null || value.isBlank() ? null : Instant.parse(value);
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static Set<MemoryScope> requireScopes(Set<MemoryScope> scopes) {
    Objects.requireNonNull(scopes, "scopes must not be null");
    if (scopes.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("scopes must not contain null");
    }
    return Set.copyOf(scopes);
  }

  private static String normalizeQuery(String query) {
    if (query == null || query.isBlank()) return null;
    return query.toLowerCase();
  }

  private static void validateLimit(int limit) {
    if (limit < 1) throw new IllegalArgumentException("limit must be positive");
  }
}
