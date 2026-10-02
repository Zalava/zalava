package org.zalava.tasks.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.platform.storage.private_state.ActorScopedPaths;
import org.zalava.platform.storage.yaml.YamlDocument;
import org.zalava.platform.storage.yaml.YamlParser;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;

/** Filesystem adapter for the actor-owned task partition. */
public final class ActorFileSystemTaskStore implements ActorTaskStore {
  private final ActorScopedPaths paths;

  public ActorFileSystemTaskStore(Path workspace) {
    this.paths = new ActorScopedPaths(workspace);
  }

  @Override
  public Task save(Actor actor, ActorTaskReference reference, Task task) {
    Path file = file(actor, reference);
    Map<String, String> frontmatter = new LinkedHashMap<>();
    frontmatter.put("task", task.getName());
    frontmatter.put("createdAt", task.getCreatedAt().toString());
    frontmatter.put("status", task.getStatus().name());
    frontmatter.put("description", task.getGoalDescription());
    task.getAgentFeedback().ifPresent(value -> frontmatter.put("agentFeedback", value));
    task.getFailureDetail().ifPresent(value -> frontmatter.put("failureDetail", value));
    try {
      Files.writeString(
          file,
          YamlParser.serialize(new YamlDocument(frontmatter, null)),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to persist actor task", exception);
    }
    return new Task(
        reference.value(),
        task.getName(),
        task.getCreatedAt(),
        task.getStatus(),
        task.getGoalDescription(),
        task.getAgentFeedback().orElse(null),
        task.getFailureDetail().orElse(null));
  }

  @Override
  public Task get(Actor actor, ActorTaskReference reference) {
    try {
      Map<String, String> frontmatter =
          YamlParser.parse(Files.readString(file(actor, reference))).frontmatter();
      return new Task(
          reference.value(),
          frontmatter.get("task"),
          Instant.parse(frontmatter.get("createdAt")),
          Task.Status.valueOf(frontmatter.get("status")),
          frontmatter.getOrDefault("description", ""),
          frontmatter.get("agentFeedback"),
          frontmatter.get("failureDetail"));
    } catch (IOException exception) {
      throw new TaskNotFoundException(reference.value(), exception);
    }
  }

  @Override
  public List<ActorTaskReference> list(Actor actor) {
    Path directory = paths.directory(actor, "tasks");
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".yaml"))
          .map(name -> name.substring(0, name.length() - ".yaml".length()))
          .map(ActorTaskReference::new)
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to list actor tasks", exception);
    }
  }

  private Path file(Actor actor, ActorTaskReference reference) {
    return paths.file(actor, "tasks", reference.value() + ".yaml");
  }
}
