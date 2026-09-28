package org.zalava.tasks.adapter.out.filesystem;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.zalava.tasks.domain.TaskReference;

@Component
public class FileSystemTaskRepository implements TaskStore {

  private final Path workspace;

  public FileSystemTaskRepository(@Value("${agent.workspace:Unknown}") Resource workspaceDir)
      throws IOException {
    this.workspace = workspaceDir.getFilePath();
  }

  @Override
  public Task save(Task task) {
    Path path =
        ofNullable(task.getId()).map(this::existingTaskPath).orElseGet(() -> buildTaskPath(task));
    writeTaskFile(path, task);
    return new Task(
        path.toAbsolutePath().toString(),
        task.getName(),
        task.getCreatedAt(),
        task.getStatus(),
        task.getGoalDescription(),
        task.getAgentFeedback().orElse(null),
        task.getFailureDetail().orElse(null));
  }

  @Override
  public Task getTaskById(String id) {
    try {
      Path path = taskPath(id);
      Map<String, String> fm = YamlParser.parse(Files.readString(path)).frontmatter();
      return new Task(
          id,
          fm.get("task"),
          Instant.parse(fm.get("createdAt")),
          Task.Status.valueOf(fm.get("status")),
          fm.getOrDefault("description", ""),
          ofNullable(fm.get("agentFeedback")).orElse(fm.get("Agent feedback")),
          fm.get("failureDetail"));
    } catch (IOException e) {
      throw new TaskNotFoundException(id, e);
    }
  }

  @Override
  public Task getTask(TaskReference reference) {
    Path root = taskDir().toAbsolutePath().normalize();
    Path path = root.resolve(reference.date().toString()).resolve(reference.filename()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path) || !isTaskFile(path)) {
      throw new TaskNotFoundException(reference.path());
    }
    try {
      Path realRoot = root.toRealPath();
      Path realPath = path.toRealPath();
      if (!realPath.startsWith(realRoot)) {
        throw new TaskNotFoundException(reference.path());
      }
      return getTaskById(realPath.toString());
    } catch (IOException ex) {
      throw new TaskNotFoundException(reference.path());
    }
  }

  @Override
  public TaskReference getReference(Task task) {
    if (task.getId() == null) {
      throw new IllegalArgumentException("Task must be persisted before creating a reference");
    }
    Path path = Path.of(task.getId()).toAbsolutePath().normalize();
    Path root = taskDir().toAbsolutePath().normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Task is outside the configured task directory");
    }
    Path relative = root.relativize(path);
    if (relative.getNameCount() != 2) {
      throw new IllegalArgumentException("Task does not have a supported persisted path");
    }
    return TaskReference.parse(relative.getName(0).toString(), relative.getName(1).toString());
  }

  @Override
  public List<Task> getTasks(LocalDate date, Task.Status status) {
    Path dir = taskDir().resolve(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    if (!Files.exists(dir)) return List.of();
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .map(p -> getTaskById(p.toAbsolutePath().toString()))
          .filter(t -> status == null || t.getStatus() == status)
          .toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to list tasks for " + date, e);
    }
  }

  @Override
  public List<Task> getAllTasks() {
    if (!Files.exists(taskDir())) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(taskDir(), 2)) {
      return files
          .filter(Files::isRegularFile)
          .filter(FileSystemTaskRepository::isTaskFile)
          .map(path -> getTaskById(path.toAbsolutePath().toString()))
          .toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to list all tasks", e);
    }
  }

  @Override
  public RecurringTask save(RecurringTask recurringTask) {
    String validName = sanitizeName(recurringTask.getName());
    Path dir = ensureDirectory(taskDir().resolve("recurring"));
    Path path = dir.resolve(validName + ".md");
    writeRecurringTaskFile(path, recurringTask);
    return new RecurringTask(
        path.toAbsolutePath().toString(), recurringTask.getName(), recurringTask.getDescription());
  }

  @Override
  public RecurringTask getRecurringTaskById(String id) {
    try {
      Path path = Path.of(id);
      Map<String, String> fm = YamlParser.parse(Files.readString(path)).frontmatter();
      return new RecurringTask(id, fm.get("task"), fm.getOrDefault("description", ""));
    } catch (IOException e) {
      throw new TaskNotFoundException(id, e);
    }
  }

  @Override
  public List<RecurringTask> getAllRecurringTasks() {
    try {
      Path dir = ensureDirectory(taskDir().resolve("recurring"));
      try (Stream<Path> recurringTasks = Files.list(dir)) {
        return recurringTasks
            .map(p -> p.toAbsolutePath().toString())
            .map(this::getRecurringTaskById)
            .toList();
      }
    } catch (IOException e) {
      throw new RuntimeException("Could not list all recurring tasks", e);
    }
  }

  @Override
  public void deleteRecurringTask(String id) {
    try {
      Files.deleteIfExists(Path.of(id));
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete recurring task file: " + id, e);
    }
  }

  // --- private helpers ---

  private Path buildTaskPath(Task task) {
    LocalDateTime dateTime = task.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
    String dateStr = dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String timeStr = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
    String safeName = sanitizeName(task.getName());
    Path dir = ensureDirectory(taskDir().resolve(dateStr));
    return dir.resolve(String.format("%s-%s.md", timeStr, safeName));
  }

  private Path taskPath(String id) throws IOException {
    Path root = taskDir().toAbsolutePath().normalize();
    Path path = Path.of(id).toAbsolutePath().normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path) || !isTaskFile(path)) {
      throw new TaskNotFoundException(
          id, new IOException("Task is outside the controlled task root"));
    }
    Path realRoot = root.toRealPath();
    Path realPath = path.toRealPath();
    if (!realPath.startsWith(realRoot)) {
      throw new TaskNotFoundException(
          id, new IOException("Task resolves outside the controlled task root"));
    }
    return realPath;
  }

  private Path existingTaskPath(String id) {
    try {
      return taskPath(id);
    } catch (IOException exception) {
      throw new TaskNotFoundException(id, exception);
    }
  }

  private void writeTaskFile(Path path, Task task) {
    Map<String, String> fm = new LinkedHashMap<>();
    fm.put("task", task.getName());
    fm.put("createdAt", task.getCreatedAt().toString());
    fm.put("status", task.getStatus().toString());
    fm.put("description", task.getGoalDescription());
    task.getAgentFeedback().ifPresent(feedback -> fm.put("agentFeedback", feedback));
    task.getFailureDetail().ifPresent(detail -> fm.put("failureDetail", detail));
    writeFile(path, YamlParser.serialize(new YamlDocument(fm, null)));
  }

  private void writeRecurringTaskFile(Path path, RecurringTask task) {
    Map<String, String> fm = new LinkedHashMap<>();
    fm.put("task", task.getName());
    fm.put("description", task.getDescription());
    writeFile(path, YamlParser.serialize(new YamlDocument(fm, null)));
  }

  private static void writeFile(Path path, String content) {
    try {
      if (!Files.exists(path)) Files.createFile(path);
      Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write task file: " + path, e);
    }
  }

  private static Path ensureDirectory(Path dir) {
    try {
      Files.createDirectories(dir);
      return dir;
    } catch (IOException e) {
      throw new RuntimeException("Failed to create directory: " + dir, e);
    }
  }

  static String sanitizeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("_{2,}", "_");
  }

  private static boolean isTaskFile(Path path) {
    if (!path.getFileName().toString().endsWith(".md")) {
      return false;
    }
    Path parent = path.getParent();
    if (parent == null) {
      return false;
    }
    try {
      LocalDate.parse(parent.getFileName().toString());
      return true;
    } catch (DateTimeParseException ignored) {
      return false;
    }
  }

  private Path taskDir() {
    return workspace.resolve("tasks");
  }
}
