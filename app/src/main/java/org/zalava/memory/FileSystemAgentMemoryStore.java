package org.zalava.memory;

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
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;

@Component
public class FileSystemAgentMemoryStore implements AgentMemoryStore {

  private static final String EXTENSION = ".yaml";

  private final Path memoryDir;

  public FileSystemAgentMemoryStore(@Value("${agent.workspace:Unknown}") Resource workspaceDir)
      throws IOException {
    this.memoryDir = workspaceDir.getFilePath().resolve("memory");
  }

  @Override
  public synchronized AgentMemory remember(AgentMemoryDraft draft) {
    AgentMemory memory =
        new AgentMemory(
            UUID.randomUUID().toString(),
            draft.scope(),
            draft.text(),
            draft.metadata(),
            Instant.now());
    Path file = ensureDirectory(memoryDir).resolve(fileName(memory));
    try {
      Files.writeString(
          file,
          YamlParser.serialize(new YamlDocument(frontmatter(memory), null)),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to save memory: " + memory.id(), ex);
    }
    return memory;
  }

  @Override
  public synchronized List<AgentMemory> recent(int limit) {
    validateLimit(limit);
    return all().stream()
        .sorted(
            Comparator.comparing(AgentMemory::createdAt).reversed().thenComparing(AgentMemory::id))
        .limit(limit)
        .toList();
  }

  @Override
  public synchronized List<AgentMemory> search(String query, int limit) {
    validateLimit(limit);
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String normalized = query.toLowerCase();
    return all().stream()
        .filter(memory -> matches(memory, normalized))
        .sorted(
            Comparator.comparing(AgentMemory::createdAt).reversed().thenComparing(AgentMemory::id))
        .limit(limit)
        .toList();
  }

  private List<AgentMemory> all() {
    if (!Files.exists(memoryDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(memoryDir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
          .map(FileSystemAgentMemoryStore::readMemory)
          .flatMap(java.util.Optional::stream)
          .toList();
    } catch (IOException ex) {
      throw new RuntimeException("Failed to list memories", ex);
    }
  }

  private static java.util.Optional<AgentMemory> readMemory(Path file) {
    try {
      Map<String, String> fm = YamlParser.parse(Files.readString(file)).frontmatter();
      return java.util.Optional.of(
          new AgentMemory(
              required(fm, "id"),
              AgentMemoryScope.valueOf(required(fm, "scope")),
              decode(required(fm, "text")),
              metadata(fm),
              Instant.parse(required(fm, "createdAt"))));
    } catch (RuntimeException | IOException ex) {
      return java.util.Optional.empty();
    }
  }

  private static Map<String, String> frontmatter(AgentMemory memory) {
    Map<String, String> fm = new LinkedHashMap<>();
    fm.put("id", memory.id());
    fm.put("scope", memory.scope().name());
    fm.put("createdAt", memory.createdAt().toString());
    fm.put("text", encode(memory.text()));
    memory.metadata().forEach((key, value) -> fm.put("metadata." + key, encode(value)));
    return fm;
  }

  private static Map<String, String> metadata(Map<String, String> fm) {
    Map<String, String> metadata = new LinkedHashMap<>();
    fm.forEach(
        (key, value) -> {
          if (key.startsWith("metadata.")) {
            metadata.put(key.substring("metadata.".length()), decode(value));
          }
        });
    return metadata;
  }

  private static boolean matches(AgentMemory memory, String query) {
    return memory.text().toLowerCase().contains(query)
        || memory.scope().name().toLowerCase().contains(query)
        || memory.metadata().values().stream()
            .anyMatch(value -> value.toLowerCase().contains(query));
  }

  private static String fileName(AgentMemory memory) {
    String created = memory.createdAt().toString().replace(":", "").replace(".", "-");
    return created + "-" + sanitize(memory.id()) + EXTENSION;
  }

  private static String sanitize(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static void validateLimit(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }

  private static String required(Map<String, String> fm, String key) {
    String value = fm.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing memory field: " + key);
    }
    return value;
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static Path ensureDirectory(Path dir) {
    try {
      Files.createDirectories(dir);
      return dir;
    } catch (IOException ex) {
      throw new RuntimeException("Failed to create directory: " + dir, ex);
    }
  }
}
