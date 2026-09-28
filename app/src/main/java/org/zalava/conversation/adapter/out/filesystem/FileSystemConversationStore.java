package org.zalava.conversation.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.zalava.accounts.domain.Actor;
import org.zalava.conversation.application.port.in.ActorConversations;
import org.zalava.conversation.application.port.out.ConversationStore;
import org.zalava.conversation.domain.ConversationMessage;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.private_state.ActorScopedPaths;

public final class FileSystemConversationStore implements ConversationStore, ActorConversations {
  private final Path conversationsDirectory;
  private final ActorScopedPaths actorPaths;

  public FileSystemConversationStore(Path workspaceDirectory) {
    conversationsDirectory = workspaceDirectory.resolve("conversations");
    actorPaths = new ActorScopedPaths(workspaceDirectory);
  }

  @Override
  public List<String> findConversationIds() {
    if (!Files.exists(conversationsDirectory)) return List.of();
    try (Stream<Path> files = Files.list(conversationsDirectory)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.startsWith("chat-") && name.endsWith(".yaml"))
          .map(name -> name.substring(5, name.length() - 5))
          .toList();
    } catch (IOException exception) {
      throw new RuntimeException("Failed to list conversations", exception);
    }
  }

  @Override
  public List<ConversationMessage> findByConversationId(String conversationId) {
    Path file = file(conversationId);
    if (!Files.exists(file)) return List.of();
    try {
      return ConversationYamlSerializer.deserialize(
          YamlParser.parse(Files.readString(file)).body());
    } catch (IOException exception) {
      throw new RuntimeException("Failed to read conversation: " + conversationId, exception);
    }
  }

  @Override
  public void appendAll(String conversationId, List<ConversationMessage> messages) {
    saveAll(
        conversationId,
        Stream.concat(findByConversationId(conversationId).stream(), messages.stream()).toList());
  }

  @Override
  public void saveAll(String conversationId, List<ConversationMessage> messages) {
    Path file = file(conversationId);
    try {
      Files.createDirectories(file.getParent());
      String createdAt =
          Files.exists(file)
              ? YamlParser.parse(Files.readString(file))
                  .frontmatter()
                  .getOrDefault("createdAt", Instant.now().toString())
              : Instant.now().toString();
      Map<String, String> frontmatter = new LinkedHashMap<>();
      frontmatter.put("createdAt", createdAt);
      frontmatter.put("updatedAt", Instant.now().toString());
      Files.writeString(
          file,
          YamlParser.serialize(
              new YamlDocument(frontmatter, ConversationYamlSerializer.serialize(messages))),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException exception) {
      throw new RuntimeException("Failed to save conversation: " + conversationId, exception);
    }
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    try {
      Files.deleteIfExists(file(conversationId));
    } catch (IOException exception) {
      throw new RuntimeException("Failed to delete conversation: " + conversationId, exception);
    }
  }

  @Override
  public List<ConversationReference> findReferences(Actor actor) {
    Path directory = actorPaths.directory(actor, "conversations");
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".yaml"))
          .map(name -> name.substring(0, name.length() - ".yaml".length()))
          .map(ConversationReference::new)
          .toList();
    } catch (IOException exception) {
      throw new RuntimeException("Failed to list actor conversations", exception);
    }
  }

  @Override
  public List<ConversationMessage> findByReference(Actor actor, ConversationReference reference) {
    Path file = actorFile(actor, reference);
    if (!Files.exists(file)) return List.of();
    try {
      return ConversationYamlSerializer.deserialize(
          YamlParser.parse(Files.readString(file)).body());
    } catch (IOException exception) {
      throw new RuntimeException("Failed to read actor conversation", exception);
    }
  }

  @Override
  public void appendAll(
      Actor actor, ConversationReference reference, List<ConversationMessage> messages) {
    saveAll(
        actor,
        reference,
        Stream.concat(findByReference(actor, reference).stream(), messages.stream()).toList());
  }

  @Override
  public void saveAll(
      Actor actor, ConversationReference reference, List<ConversationMessage> messages) {
    Path file = actorFile(actor, reference);
    try {
      String createdAt =
          Files.exists(file)
              ? YamlParser.parse(Files.readString(file))
                  .frontmatter()
                  .getOrDefault("createdAt", Instant.now().toString())
              : Instant.now().toString();
      Files.writeString(
          file,
          YamlParser.serialize(
              new YamlDocument(
                  Map.of("createdAt", createdAt, "updatedAt", Instant.now().toString()),
                  ConversationYamlSerializer.serialize(messages))),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException exception) {
      throw new RuntimeException("Failed to save actor conversation", exception);
    }
  }

  @Override
  public void delete(Actor actor, ConversationReference reference) {
    try {
      Files.deleteIfExists(actorFile(actor, reference));
    } catch (IOException exception) {
      throw new RuntimeException("Failed to delete actor conversation", exception);
    }
  }

  private Path file(String conversationId) {
    return conversationsDirectory.resolve("chat-" + conversationId + ".yaml");
  }

  private Path actorFile(Actor actor, ConversationReference reference) {
    return actorPaths.file(actor, "conversations", reference.value() + ".yaml");
  }
}
