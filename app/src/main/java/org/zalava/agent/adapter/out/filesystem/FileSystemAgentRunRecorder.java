package org.zalava.agent.adapter.out.filesystem;

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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.zalava.accounts.domain.Actor;
import org.zalava.agent.application.port.out.ActorRunStore;
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.agent.domain.AgentRun;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.private_state.ActorScopedPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class FileSystemAgentRunRecorder implements AgentRunStore, ActorRunStore {

  private final Path recordsDir;
  private final ActorScopedPaths actorPaths;

  public FileSystemAgentRunRecorder(@Value("${agent.workspace:Unknown}") Resource workspaceDir)
      throws IOException {
    this.recordsDir = workspaceDir.getFilePath().resolve("agent-runs");
    this.actorPaths = new ActorScopedPaths(workspaceDir.getFilePath());
  }

  @Override
  public synchronized void record(AgentRun record) {
    Path file = ensureDirectory(recordsDir).resolve(fileName(record));
    try {
      Files.writeString(
          file,
          YamlParser.serialize(new YamlDocument(frontmatter(record), null)),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to save agent run record: " + record.id(), ex);
    }
  }

  @Override
  public synchronized List<AgentRun> recent() {
    return recent(recordsDir);
  }

  @Override
  public synchronized void record(Actor actor, AgentRun record) {
    Path file = actorPaths.file(actor, "agent-runs", fileName(record));
    write(file, record);
  }

  @Override
  public synchronized List<AgentRun> recent(Actor actor) {
    return recent(actorPaths.directory(actor, "agent-runs"));
  }

  private static List<AgentRun> recent(Path directory) {
    if (!Files.exists(directory)) return List.of();
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".yaml"))
          .map(FileSystemAgentRunRecorder::readRecord)
          .flatMap(java.util.Optional::stream)
          .sorted(Comparator.comparing(AgentRun::startedAt).thenComparing(AgentRun::id))
          .toList();
    } catch (IOException ex) {
      throw new RuntimeException("Failed to list agent run records", ex);
    }
  }

  private static void write(Path file, AgentRun record) {
    try {
      Files.writeString(
          file,
          YamlParser.serialize(new YamlDocument(frontmatter(record), null)),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to save agent run record: " + record.id(), ex);
    }
  }

  private static java.util.Optional<AgentRun> readRecord(Path file) {
    try {
      Map<String, String> fm = YamlParser.parse(Files.readString(file)).frontmatter();
      return java.util.Optional.of(
          new AgentRun(
              required(fm, "id"),
              fm.getOrDefault("conversationId", ""),
              AgentRun.PromptType.valueOf(required(fm, "promptType")),
              decode(fm.get("promptPreview")),
              Integer.parseInt(required(fm, "selectedToolCount")),
              Integer.parseInt(fm.getOrDefault("contextSourceCount", "0")),
              Integer.parseInt(fm.getOrDefault("contextCharacterBudget", "0")),
              Integer.parseInt(fm.getOrDefault("contextCharactersUsed", "0")),
              contextSourceMetrics(fm),
              Instant.parse(required(fm, "startedAt")),
              Instant.parse(required(fm, "completedAt")),
              Long.parseLong(required(fm, "durationMillis")),
              AgentRun.Status.valueOf(required(fm, "status")),
              decode(fm.get("resultPreview")),
              decode(fm.get("errorPreview")),
              Integer.parseInt(fm.getOrDefault("structuredAttemptCount", "0")),
              AgentRun.StructuredFailureCategory.valueOf(
                  fm.getOrDefault(
                      "structuredFailureCategory",
                      AgentRun.StructuredFailureCategory.NONE.name()))));
    } catch (RuntimeException | IOException ex) {
      return java.util.Optional.empty();
    }
  }

  private static Map<String, String> frontmatter(AgentRun record) {
    Map<String, String> fm = new LinkedHashMap<>();
    fm.put("id", record.id());
    fm.put("conversationId", record.conversationId());
    fm.put("promptType", record.promptType().name());
    fm.put("promptPreview", encode(record.promptPreview()));
    fm.put("selectedToolCount", Integer.toString(record.selectedToolCount()));
    fm.put("contextSourceCount", Integer.toString(record.contextSourceCount()));
    fm.put("contextCharacterBudget", Integer.toString(record.contextCharacterBudget()));
    fm.put("contextCharactersUsed", Integer.toString(record.contextCharactersUsed()));
    for (int i = 0; i < record.contextSourceMetrics().size(); i++) {
      AgentRun.ContextSourceMetric metric = record.contextSourceMetrics().get(i);
      String prefix = "contextSourceMetric." + i + ".";
      fm.put(prefix + "sourceType", metric.sourceType());
      fm.put(prefix + "charactersAvailable", Integer.toString(metric.charactersAvailable()));
      fm.put(prefix + "charactersUsed", Integer.toString(metric.charactersUsed()));
    }
    fm.put("startedAt", record.startedAt().toString());
    fm.put("completedAt", record.completedAt().toString());
    fm.put("durationMillis", Long.toString(record.durationMillis()));
    fm.put("status", record.status().name());
    fm.put("resultPreview", encode(record.resultPreview()));
    fm.put("errorPreview", encode(record.errorPreview()));
    fm.put("structuredAttemptCount", Integer.toString(record.structuredAttemptCount()));
    fm.put("structuredFailureCategory", record.structuredFailureCategory().name());
    return fm;
  }

  private static String fileName(AgentRun record) {
    String started = record.startedAt().toString().replace(":", "").replace(".", "-");
    return started + "-" + sanitize(record.id()) + ".yaml";
  }

  private static String sanitize(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String encode(String value) {
    if (value == null) {
      return "";
    }
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static List<AgentRun.ContextSourceMetric> contextSourceMetrics(Map<String, String> fm) {
    int count = Integer.parseInt(fm.getOrDefault("contextSourceCount", "0"));
    return IntStream.range(0, count)
        .mapToObj(
            index -> {
              String prefix = "contextSourceMetric." + index + ".";
              return new AgentRun.ContextSourceMetric(
                  required(fm, prefix + "sourceType"),
                  Integer.parseInt(required(fm, prefix + "charactersAvailable")),
                  Integer.parseInt(required(fm, prefix + "charactersUsed")));
            })
        .toList();
  }

  private static String required(Map<String, String> fm, String key) {
    String value = fm.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing agent run record field: " + key);
    }
    return value;
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
