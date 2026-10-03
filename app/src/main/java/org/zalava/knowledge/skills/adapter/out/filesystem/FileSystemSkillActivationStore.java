package org.zalava.knowledge.skills.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.skills.application.port.out.SkillActivationStore;
import org.zalava.knowledge.skills.domain.SkillActivation;
import org.zalava.platform.storage.private_state.ActorScopedPaths;
import tools.jackson.databind.ObjectMapper;

/**
 * Atomic, actor-scoped filesystem persistence for skill activations.
 *
 * <p>Activations live under {@code users/<account-id>/skill-activations} so one actor's selection
 * is invisible to another and survives a restart. Unreadable records are skipped rather than
 * failing a listing.
 */
public final class FileSystemSkillActivationStore implements SkillActivationStore {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final String AREA = "skill-activations";

  private final ActorScopedPaths paths;

  public FileSystemSkillActivationStore(Path workspace) {
    this.paths = new ActorScopedPaths(workspace);
  }

  @Override
  public SkillActivation save(SkillActivation activation) {
    Path target = paths.file(actorOf(activation), AREA, fileName(activation.name()));
    Path temporary = target.resolveSibling(activation.name() + ".json.tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), activation);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return activation;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to persist Zalava skill activation: " + activation.name(), exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public Optional<SkillActivation> find(Actor actor, String name) {
    Path file = paths.file(actor, AREA, fileName(name));
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return Optional.of(read(file));
  }

  @Override
  public List<SkillActivation> list(Actor actor) {
    Path directory;
    try {
      directory = paths.directory(actor, AREA);
    } catch (IllegalArgumentException exception) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .map(this::readIfValid)
          .flatMap(Optional::stream)
          .sorted(Comparator.comparing(SkillActivation::name))
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to list Zalava skill activations", exception);
    }
  }

  private Optional<SkillActivation> readIfValid(Path file) {
    try {
      return Optional.of(read(file));
    } catch (IllegalStateException exception) {
      return Optional.empty();
    }
  }

  private SkillActivation read(Path file) {
    try {
      return JSON.readValue(file.toFile(), SkillActivation.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Unable to read Zalava skill activation: " + file, exception);
    }
  }

  private static String fileName(String name) {
    if (name == null || !KEBAB_CASE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid Zalava skill name");
    }
    return name + ".json";
  }

  private static Actor actorOf(SkillActivation activation) {
    try {
      return new Actor(new AccountId(UUID.fromString(activation.actorId())));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid Zalava skill activation owner", exception);
    }
  }
}
