package org.zalava.tasks.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

/**
 * Covers the {@link FileSystemTaskRepository} boundary branches left out of the base test:
 * reference-shape validation, recurring-task deletion, missing directories, and save round-trips
 * that exercise the write path through the real filesystem.
 */
class FileSystemTaskRepositoryBoundariesTest {

  @TempDir Path workspaceDir;
  FileSystemTaskRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new FileSystemTaskRepository(new FileSystemResource(workspaceDir));
  }

  @Test
  void getReferenceRejectsUnpersistedTasks() {
    assertThatThrownBy(() -> repository.getReference(Task.newTask("unpersisted", "goal")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task must be persisted before creating a reference");
  }

  @Test
  void getReferenceRejectsTasksOutsideTheTaskDirectory() throws Exception {
    Path outside = Files.writeString(workspaceDir.resolve("external.md"), "not a task");

    assertThatThrownBy(
            () ->
                repository.getReference(
                    new Task(
                        outside.toAbsolutePath().toString(),
                        "external",
                        NOW(),
                        Task.Status.todo,
                        "goal")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task is outside the configured task directory");
  }

  @Test
  void getReferenceRejectsUnsupportedPersistedPathShapes() throws Exception {
    Path nestedDirectory = Files.createDirectories(workspaceDir.resolve("tasks/2026-06-08/deep"));
    Path nested = Files.writeString(nestedDirectory.resolve("x.md"), "placeholder");

    assertThatThrownBy(
            () ->
                repository.getReference(
                    new Task(nested.toString(), "nested", NOW(), Task.Status.todo, "goal")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Task does not have a supported persisted path");
  }

  @Test
  void getTasksForAMissingDateYieldsAnEmptyList() {
    assertThat(repository.getTasks(LocalDate.of(2026, 1, 1), null)).isEmpty();
  }

  @Test
  void getAllTasksOnAnEmptyWorkspaceYieldsAnEmptyList() {
    assertThat(repository.getAllTasks()).isEmpty();
  }

  @Test
  void getTasksSkipsFilesThatAreNotMarkdownTasks() throws Exception {
    repository.save(Task.newTask("real-task", "Real goal"));
    Path dateDirectory = workspaceDir.resolve("tasks").resolve(LocalDate.now().toString());
    Files.writeString(dateDirectory.resolve("notes.txt"), "ignored");

    List<Task> tasks = repository.getTasks(LocalDate.now(), null);

    assertThat(tasks).hasSize(1);
  }

  @Test
  void recurringTaskRoundTripIncludesDeleteAndListing() {
    RecurringTask first =
        repository.save(RecurringTask.newRecurringTask("check-mail", "Check inbox"));
    repository.save(RecurringTask.newRecurringTask("water-plants", "Water the plants"));

    assertThat(repository.getAllRecurringTasks())
        .extracting(RecurringTask::getName)
        .containsExactlyInAnyOrder("check-mail", "water-plants");

    RecurringTask loaded = repository.getRecurringTaskById(first.getId());
    assertThat(loaded.getDescription()).isEqualTo("Check inbox");

    repository.deleteRecurringTask(first.getId());
    assertThat(repository.getAllRecurringTasks())
        .extracting(RecurringTask::getName)
        .containsExactly("water-plants");
    repository.deleteRecurringTask(first.getId());
  }

  @Test
  void getRecurringTaskByIdThrowsForMissingFiles() {
    assertThatThrownBy(
            () -> repository.getRecurringTaskById(workspaceDir.resolve("absent.md").toString()))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void savePersistsAndReloadsFailureDetailsThroughASecondRepositoryInstance() throws Exception {
    var first = new FileSystemTaskRepository(new FileSystemResource(workspaceDir));
    Task saved =
        first.save(
            Task.newTask("handle-email", NOW(), "Process unread email messages")
                .withStatus(Task.Status.failed)
                .withFeedback("Partial progress.")
                .withFailureDetail("Rate limited twice."));

    var second = new FileSystemTaskRepository(new FileSystemResource(workspaceDir));
    Task loaded = second.getTaskById(saved.getId());

    assertThat(loaded.getStatus()).isEqualTo(Task.Status.failed);
    assertThat(loaded.getAgentFeedback()).contains("Partial progress.");
    assertThat(loaded.getFailureDetail()).contains("Rate limited twice.");
  }

  @Test
  void sanitizeNameReplacesIllegalCharactersAndCollapsesUnderscores() {
    assertThat(FileSystemTaskRepository.sanitizeName("weird---name!!")).isEqualTo("weird---name_");
    assertThat(FileSystemTaskRepository.sanitizeName("__leading__")).isEqualTo("_leading_");
    assertThat(FileSystemTaskRepository.sanitizeName("plain-name.md")).isEqualTo("plain-name.md");
  }

  @Test
  void getAllTasksIncludesRecurringDirectoryTasksOnlyThroughTheDateShapeFilter() {
    repository.save(Task.newTask("today-task", "Current task"));
    repository.save(RecurringTask.newRecurringTask("daily", "template"));

    assertThat(repository.getAllTasks()).extracting(Task::getName).containsExactly("today-task");
  }

  private static Instant NOW() {
    return Instant.parse("2026-09-03T10:00:00Z");
  }
}
