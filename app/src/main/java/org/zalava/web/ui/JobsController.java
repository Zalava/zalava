package org.zalava.web.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

@Controller
public class JobsController {

  private static final DateTimeFormatter CREATED_AT =
      DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH);

  private final TaskQueries taskQueries;
  private final ActorTaskCommands actorTasks;
  private final AuthenticatedActorResolver actors;
  private final org.zalava.tasks.application.port.in.ActorJobEvidenceQueries evidenceQueries;

  public JobsController(
      TaskQueries taskQueries,
      ActorTaskCommands actorTasks,
      AuthenticatedActorResolver actors,
      org.zalava.tasks.application.port.in.ActorJobEvidenceQueries evidenceQueries) {
    this.taskQueries = taskQueries;
    this.actorTasks = actorTasks;
    this.actors = actors;
    this.evidenceQueries = evidenceQueries;
  }

  @GetMapping("/jobs")
  public String jobs(Model model, Authentication authentication) {
    model.addAttribute(
        "model",
        actors
            .actorIfAuthenticated(authentication)
            .map(this::buildActorModel)
            .orElseGet(this::buildModel));
    model.addAttribute(
        "evidence",
        actors
            .actorIfAuthenticated(authentication)
            .map(evidenceQueries::snapshot)
            .orElseGet(
                org.zalava.tasks.application.port.in.ActorJobEvidenceQueries.Snapshot::empty));
    return "ui/jobs";
  }

  private JobsModel buildActorModel(Actor actor) {
    List<ActorJob> tasks =
        actorTasks.list(actor).stream()
            .map(reference -> new ActorJob(reference, actorTasks.get(actor, reference)))
            .sorted(
                Comparator.comparing(job -> job.task().getCreatedAt(), Comparator.reverseOrder()))
            .toList();
    return new JobsModel(
        actorGroup("running", "Running", "is-info is-light", tasks, Task.Status.in_progress),
        actorGroup(
            "waiting",
            "Waiting for Input",
            "is-warning is-light",
            tasks,
            Task.Status.awaiting_human_input),
        actorGroup("queued", "Queued", "is-light", tasks, Task.Status.todo),
        actorGroup("completed", "Completed", "is-success is-light", tasks, Task.Status.completed),
        actorGroup("failed", "Failed", "is-danger is-light", tasks, Task.Status.failed),
        tasks.size());
  }

  private JobGroup actorGroup(
      String key, String title, String statusClass, List<ActorJob> tasks, Task.Status status) {
    List<JobEntry> entries =
        tasks.stream()
            .filter(job -> job.task().getStatus() == status)
            .map(this::toJobEntry)
            .toList();
    return new JobGroup(key, title, statusClass, entries);
  }

  private JobsModel buildModel() {
    List<Task> tasks =
        taskQueries.getAllTasks().stream()
            .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
            .toList();
    return new JobsModel(
        group("running", "Running", "is-info is-light", tasks, Task.Status.in_progress),
        group(
            "waiting",
            "Waiting for Input",
            "is-warning is-light",
            tasks,
            Task.Status.awaiting_human_input),
        group("queued", "Queued", "is-light", tasks, Task.Status.todo),
        group("completed", "Completed", "is-success is-light", tasks, Task.Status.completed),
        group("failed", "Failed", "is-danger is-light", tasks, Task.Status.failed),
        tasks.size());
  }

  private JobGroup group(
      String key, String title, String statusClass, List<Task> tasks, Task.Status status) {
    List<JobEntry> entries =
        tasks.stream().filter(task -> task.getStatus() == status).map(this::toJobEntry).toList();
    return new JobGroup(key, title, statusClass, entries);
  }

  private JobEntry toJobEntry(Task task) {
    TaskReference reference = taskQueries.getReference(task);
    return new JobEntry(
        task.getName(),
        task.getDescription(),
        CREATED_AT.format(task.getCreatedAt().atZone(ZoneId.systemDefault())),
        "/jobs/" + reference.path());
  }

  private JobEntry toJobEntry(ActorJob job) {
    return new JobEntry(
        job.task().getName(),
        job.task().getDescription(),
        CREATED_AT.format(job.task().getCreatedAt().atZone(ZoneId.systemDefault())),
        "/jobs/" + job.reference().value());
  }

  private record ActorJob(ActorTaskReference reference, Task task) {}

  public record JobsModel(
      JobGroup running,
      JobGroup waiting,
      JobGroup queued,
      JobGroup completed,
      JobGroup failed,
      int totalJobs) {
    public List<JobGroup> groups() {
      return List.of(running, waiting, queued, completed, failed);
    }
  }

  public record JobGroup(String key, String title, String statusClass, List<JobEntry> jobs) {}

  public record JobEntry(String name, String description, String createdAt, String url) {}
}
