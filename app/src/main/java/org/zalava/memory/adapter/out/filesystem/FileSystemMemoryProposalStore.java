package org.zalava.memory.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.memory.application.port.out.MemoryProposalStore;
import org.zalava.memory.domain.MemoryProposal;
import org.zalava.private_state.ActorScopedPaths;
import tools.jackson.databind.ObjectMapper;

/**
 * Atomic, actor-scoped filesystem persistence for durable-memory proposals. Proposals live under
 * {@code users/<account-id>/memory-proposals} so a proposal is invisible to another actor and
 * survives a restart. Unreadable records are skipped rather than failing a listing.
 */
public final class FileSystemMemoryProposalStore implements MemoryProposalStore {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String AREA = "memory-proposals";

  private final ActorScopedPaths paths;

  public FileSystemMemoryProposalStore(Path workspace) {
    this.paths = new ActorScopedPaths(workspace);
  }

  @Override
  public MemoryProposal save(MemoryProposal proposal) {
    Path target = paths.file(actorOf(proposal), AREA, proposal.id() + ".json");
    Path temporary = target.resolveSibling(proposal.id() + ".json.tmp");
    try {
      JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), proposal);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return proposal;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to persist SEA memory proposal: " + proposal.id(), exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public Optional<MemoryProposal> find(Actor actor, String proposalId) {
    Path file = paths.file(actor, AREA, fileName(proposalId));
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return Optional.of(read(file));
  }

  @Override
  public List<MemoryProposal> list(Actor actor) {
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
          .sorted(Comparator.comparing(MemoryProposal::createdAt).reversed())
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to list SEA memory proposals", exception);
    }
  }

  private Optional<MemoryProposal> readIfValid(Path file) {
    try {
      return Optional.of(read(file));
    } catch (IllegalStateException exception) {
      return Optional.empty();
    }
  }

  private MemoryProposal read(Path file) {
    try {
      return JSON.readValue(file.toFile(), MemoryProposal.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Unable to read SEA memory proposal: " + file, exception);
    }
  }

  private static String fileName(String proposalId) {
    try {
      UUID.fromString(proposalId);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid SEA memory proposal id", exception);
    }
    return proposalId + ".json";
  }

  private static Actor actorOf(MemoryProposal proposal) {
    try {
      return new Actor(new AccountId(UUID.fromString(proposal.actorId())));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid SEA memory proposal owner", exception);
    }
  }
}
