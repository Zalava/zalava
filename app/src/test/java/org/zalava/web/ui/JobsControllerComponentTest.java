package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.MutableWorkspaceComponentTest;
import org.zalava.support.SeaComponentTestInitializer;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.Task;

@MutableWorkspaceComponentTest
class JobsControllerComponentTest {

  private static final Path WORKSPACE = SeaComponentTestInitializer.workspacePath();

  @Autowired private MockMvc mockMvc;

  @Autowired private TaskStore taskRepository;

  @BeforeEach
  void clearTasks() throws IOException {
    Path tasks = WORKSPACE.resolve("tasks");
    if (!Files.exists(tasks)) {
      return;
    }
    try (var paths = Files.walk(tasks)) {
      paths
          .sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(tasks))
          .forEach(JobsControllerComponentTest::delete);
    }
  }

  @Test
  void rendersJobsGroupedByPersistedStatusAndNewestFirst() throws Exception {
    Instant now = Instant.now();
    Task older =
        saveTask("Older running job", now.minus(2, ChronoUnit.DAYS), Task.Status.in_progress);
    Task newest = saveTask("Newest running job", now, Task.Status.in_progress);
    saveTask("Waiting job", now.minus(1, ChronoUnit.HOURS), Task.Status.awaiting_human_input);
    saveTask("Queued job", now.minus(2, ChronoUnit.HOURS), Task.Status.todo);
    saveTask("Completed job", now.minus(3, ChronoUnit.HOURS), Task.Status.completed);
    saveTask("Failed job", now.minus(4, ChronoUnit.HOURS), Task.Status.failed);

    mockMvc
        .perform(get("/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Jobs</title>")))
        .andExpect(content().string(containsString("aria-current=\"page\" href=\"/jobs\"")))
        .andExpect(content().string(containsString("data-group=\"running\" data-count=\"2\"")))
        .andExpect(content().string(containsString("data-group=\"waiting\" data-count=\"1\"")))
        .andExpect(content().string(containsString("data-group=\"queued\" data-count=\"1\"")))
        .andExpect(content().string(containsString("data-group=\"completed\" data-count=\"1\"")))
        .andExpect(content().string(containsString("data-group=\"failed\" data-count=\"1\"")))
        .andExpect(content().string(containsString("Waiting for Input")))
        .andExpect(content().string(containsString("Failed job")))
        .andExpect(content().string(containsString("Jobs component test task.")))
        .andExpect(content().string(containsString("href=\"" + jobUrl(newest) + "\"")))
        .andExpect(content().string(containsString("href=\"" + jobUrl(older) + "\"")))
        .andExpect(
            content().string(stringContainsInOrder("Newest running job", "Older running job")))
        .andExpect(content().string(containsString("Failed")));
  }

  @Test
  void rendersEmptyJobsState() throws Exception {
    mockMvc
        .perform(get("/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No jobs have been created yet.")))
        .andExpect(content().string(containsString("href=\"/chat\"")));
  }

  private Task saveTask(String name, Instant createdAt, Task.Status status) {
    return taskRepository.save(
        new Task(null, name, createdAt, status, "Jobs component test task."));
  }

  private static String jobUrl(Task task) {
    Path path = Path.of(task.getId());
    return "/jobs/" + path.getParent().getFileName() + "/" + path.getFileName();
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to clean jobs test workspace", ex);
    }
  }
}
