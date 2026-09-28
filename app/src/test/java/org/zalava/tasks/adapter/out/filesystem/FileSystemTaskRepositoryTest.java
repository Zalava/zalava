package org.zalava.tasks.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.zalava.tasks.domain.TaskReference;

class FileSystemTaskRepositoryTest {

  @TempDir Path workspaceDir;
  FileSystemTaskRepository repository;

  @BeforeEach
  void setUp() throws IOException {
    repository = new FileSystemTaskRepository(new FileSystemResource(workspaceDir));
  }

  @Test
  void saveTaskCreatesFileWithCorrectContent() throws IOException {
    Task task = Task.newTask("handle-email", "Process unread email messages");

    Task saved = repository.save(task);

    List<Path> files = listTaskFiles(LocalDate.now());
    assertThat(files).hasSize(1);
    assertThat(files.getFirst().getFileName().toString()).matches("\\d{6}-handle-email\\.md");
    assertThat(Files.readString(files.getFirst()))
        .contains("task: handle-email")
        .contains("status: todo")
        .contains("description: Process unread email messages");
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getName()).isEqualTo("handle-email");
    assertThat(saved.getStatus()).isEqualTo(Task.Status.todo);
  }

  @Test
  void saveTaskUpdatesExistingFile() {
    Task task = repository.save(Task.newTask("handle-email", "Process unread email messages"));

    Task updated = repository.save(task.withStatus(Task.Status.in_progress));

    Task reloaded = repository.getTaskById(task.getId());
    assertThat(reloaded.getStatus()).isEqualTo(Task.Status.in_progress);
    assertThat(updated.getId()).isEqualTo(task.getId());
  }

  @Test
  void getTaskByIdReturnsCorrectTask() {
    Task saved = repository.save(Task.newTask("handle-email", "Process unread email messages"));

    Task loaded = repository.getTaskById(saved.getId());

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getName()).isEqualTo("handle-email");
    assertThat(loaded.getStatus()).isEqualTo(Task.Status.todo);
    assertThat(loaded.getDescription()).isEqualTo("Process unread email messages");
    assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @Test
  void getTaskByIdRejectsAPathOutsideTheControlledTaskRoot() throws IOException {
    Path outside = Files.writeString(workspaceDir.resolve("outside.md"), "not a task");

    assertThatThrownBy(() -> repository.getTaskById(outside.toString()))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void savesAndLoadsAgentFeedbackAsASeparateField() throws IOException {
    Task saved =
        repository.save(
            Task.newTask("handle-email", "Process unread email messages")
                .withFeedback("Processed three messages."));

    String persisted = Files.readString(Path.of(saved.getId()));
    Task loaded = repository.getTaskById(saved.getId());

    assertThat(persisted)
        .contains("description: Process unread email messages")
        .contains("agentFeedback: Processed three messages.")
        .doesNotContain("Agent feedback:");
    assertThat(loaded.getGoalDescription()).isEqualTo("Process unread email messages");
    assertThat(loaded.getAgentFeedback()).contains("Processed three messages.");
  }

  @Test
  void loadsLegacyAgentFeedbackFrontmatterKey() throws IOException {
    Path taskDir = Files.createDirectories(workspaceDir.resolve("tasks/2026-06-08"));
    Path taskFile = taskDir.resolve("120000-legacy.md");
    Files.writeString(
        taskFile,
        """
                ---
                task: legacy
                createdAt: 2026-06-08T12:00:00Z
                status: completed
                description: Original goal.

                Agent feedback: Finished the legacy task.
                """);

    Task loaded = repository.getTaskById(taskFile.toString());

    assertThat(loaded.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(loaded.getAgentFeedback()).contains("Finished the legacy task.");
  }

  @Test
  void savesAndLoadsFailureDetailAsSeparateMetadata() throws IOException {
    Task saved =
        repository.save(
            Task.newTask("handle-email", "Process unread email messages")
                .withFeedback("Processed one message before stopping.")
                .withStatus(Task.Status.failed)
                .withFailureDetail("SEA could not complete this job after multiple attempts."));

    String persisted = Files.readString(Path.of(saved.getId()));
    Task loaded = repository.getTaskById(saved.getId());

    assertThat(persisted)
        .contains("status: failed")
        .contains("agentFeedback: Processed one message before stopping.")
        .contains("failureDetail: SEA could not complete this job after multiple attempts.");
    assertThat(loaded.getAgentFeedback()).contains("Processed one message before stopping.");
    assertThat(loaded.getFailureDetail())
        .contains("SEA could not complete this job after multiple attempts.");
  }

  @Test
  void loadsExistingTaskFileWithoutFailureDetail() throws IOException {
    Path taskDir = Files.createDirectories(workspaceDir.resolve("tasks/2026-06-08"));
    Path taskFile = taskDir.resolve("120000-existing.md");
    Files.writeString(
        taskFile,
        """
                ---
                task: existing
                createdAt: 2026-06-08T12:00:00Z
                status: completed
                description: Existing task format.
                ---
                """);

    Task loaded = repository.getTaskById(taskFile.toString());

    assertThat(loaded.getFailureDetail()).isEmpty();
  }

  @Test
  void removesFailureDetailFromInconsistentNonFailedTaskFile() throws IOException {
    Path taskDir = Files.createDirectories(workspaceDir.resolve("tasks/2026-06-08"));
    Path taskFile = taskDir.resolve("120000-inconsistent.md");
    Files.writeString(
        taskFile,
        """
                ---
                task: inconsistent
                createdAt: 2026-06-08T12:00:00Z
                status: completed
                description: Existing task format.
                failureDetail: secret token at /private/key.txt
                ---
                """);

    Task loaded = repository.getTaskById(taskFile.toString());
    repository.save(loaded);

    assertThat(loaded.getFailureDetail()).isEmpty();
    assertThat(Files.readString(taskFile))
        .doesNotContain("failureDetail")
        .doesNotContain("/private/key.txt")
        .doesNotContain("secret token");
  }

  @Test
  void resolvesTaskThroughSafeReference() {
    Task saved = repository.save(Task.newTask("handle-email", "Process unread email messages"));

    TaskReference reference = repository.getReference(saved);
    Task loaded = repository.getTask(reference);

    assertThat(reference.date()).isEqualTo(LocalDate.now());
    assertThat(reference.filename()).matches("\\d{6}-handle-email\\.md");
    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getName()).isEqualTo("handle-email");
  }

  @Test
  void resolvesPersistedTaskWithBlankName() {
    Task saved = repository.save(Task.newTask("", "Task with no display name"));

    TaskReference reference = repository.getReference(saved);
    Task loaded = repository.getTask(reference);

    assertThat(reference.filename()).matches("\\d{6}-\\.md");
    assertThat(loaded.getId()).isEqualTo(saved.getId());
  }

  @Test
  void rejectsInvalidTaskReferences() {
    assertThatThrownBy(() -> TaskReference.parse("..", "outside.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaskReference.parse(LocalDate.now().toString(), "../outside.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaskReference.parse(LocalDate.now().toString(), "nested/outside.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaskReference.parse(LocalDate.now().toString(), "task.txt"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingSafeTaskReferenceIsNotFound() {
    TaskReference reference = TaskReference.parse(LocalDate.now().toString(), "120000-missing.md");

    assertThatThrownBy(() -> repository.getTask(reference))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void safeTaskReferenceRejectsSymlinkOutsideTaskDirectory() throws IOException {
    Path outside = Files.createDirectories(workspaceDir.resolve("outside"));
    Files.writeString(
        outside.resolve("120000-escaped.md"),
        """
                ---
                task: escaped
                createdAt: 2026-06-08T10:00:00Z
                status: todo
                description: outside task directory
                ---
                """);
    Path tasks = Files.createDirectories(workspaceDir.resolve("tasks"));
    Files.createSymbolicLink(tasks.resolve("2026-06-08"), outside);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-escaped.md");

    assertThatThrownBy(() -> repository.getTask(reference))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void getTasksFiltersCorrectlyByStatus() {
    repository.save(Task.newTask("task-a", "First task"));
    Task taskB = repository.save(Task.newTask("task-b", "Second task"));
    repository.save(taskB.withStatus(Task.Status.completed));

    List<Task> todoTasks = repository.getTasks(LocalDate.now(), Task.Status.todo);
    List<Task> completedTasks = repository.getTasks(LocalDate.now(), Task.Status.completed);

    assertThat(todoTasks)
        .hasSize(1)
        .first()
        .satisfies(t -> assertThat(t.getName()).isEqualTo("task-a"));
    assertThat(completedTasks)
        .hasSize(1)
        .first()
        .satisfies(t -> assertThat(t.getName()).isEqualTo("task-b"));
  }

  @Test
  void getTasksReturnsAllTasksWhenStatusIsNull() {
    repository.save(Task.newTask("task-a", "First task"));
    repository.save(Task.newTask("task-b", "Second task"));

    List<Task> tasks = repository.getTasks(LocalDate.now(), null);

    assertThat(tasks).hasSize(2);
  }

  @Test
  void getAllTasksReturnsTasksAcrossDatesAndExcludesRecurringTemplates() {
    Instant yesterday =
        LocalDate.now().minusDays(1).atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant();
    repository.save(Task.newTask("yesterday-task", yesterday, "Older task"));
    repository.save(Task.newTask("today-task", "Current task"));
    repository.save(RecurringTask.newRecurringTask("daily-check", "Recurring template"));

    List<Task> tasks = repository.getAllTasks();

    assertThat(tasks)
        .extracting(Task::getName)
        .containsExactlyInAnyOrder("yesterday-task", "today-task");
  }

  @Test
  void saveTaskUsesScheduledDateForDirectory() throws IOException {
    LocalDateTime futureTime = LocalDateTime.now().plusDays(3);
    Task task =
        Task.newTask(
            "task-a", futureTime.atZone(ZoneId.systemDefault()).toInstant(), "Future task");

    repository.save(task);

    List<Path> files = listTaskFiles(futureTime.toLocalDate());
    assertThat(files).hasSize(1);
  }

  @Test
  void saveRecurringTaskCreatesFileWithCorrectContent() throws IOException {
    RecurringTask recurringTask =
        RecurringTask.newRecurringTask("check-mail", "Check inbox every 15 minutes");

    RecurringTask saved = repository.save(recurringTask);

    List<Path> files = listRecurringTaskFiles();
    assertThat(files).hasSize(1);
    assertThat(files.getFirst().getFileName().toString()).isEqualTo("check-mail.md");
    assertThat(Files.readString(files.getFirst()))
        .contains("task: check-mail")
        .contains("description: Check inbox every 15 minutes");
    assertThat(saved.getId()).isNotNull();
  }

  @Test
  void getRecurringTaskByIdReturnsCorrectTask() {
    RecurringTask saved =
        repository.save(
            RecurringTask.newRecurringTask("check-mail", "Check inbox every 15 minutes"));

    RecurringTask loaded = repository.getRecurringTaskById(saved.getId());

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getName()).isEqualTo("check-mail");
    assertThat(loaded.getDescription()).isEqualTo("Check inbox every 15 minutes");
  }

  private List<Path> listTaskFiles(LocalDate date) throws IOException {
    return listTaskFiles(date.toString());
  }

  private List<Path> listRecurringTaskFiles() throws IOException {
    return listTaskFiles("recurring");
  }

  private List<Path> listTaskFiles(String subDir) throws IOException {
    Path dir = workspaceDir.resolve("tasks").resolve(subDir);
    assertThat(dir).isDirectory();
    return listFiles(dir);
  }

  private List<Path> listFiles(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.toList();
    }
  }
}
