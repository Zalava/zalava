package org.zalava.chat.attachment.adapter.out.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.attachment.application.port.out.ChatAttachmentStore;
import org.zalava.chat.attachment.domain.ChatAttachment;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.private_state.ActorScopedPaths;

/** Actor-owned filesystem storage for task-only chat attachments. */
public final class FileSystemChatAttachmentStore implements ChatAttachmentStore {
  private static final String AREA = "attachments";

  private final ActorScopedPaths paths;

  public FileSystemChatAttachmentStore(Path workspace) {
    this.paths = new ActorScopedPaths(workspace);
  }

  @Override
  public ChatAttachment save(Actor actor, ChatAttachment attachment, byte[] content) {
    try {
      Files.write(
          blob(actor, attachment.id()),
          content,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      Map<String, String> frontmatter = new LinkedHashMap<>();
      frontmatter.put("id", attachment.id());
      frontmatter.put("displayName", attachment.displayName());
      frontmatter.put("contentType", attachment.contentType());
      frontmatter.put("byteCount", Long.toString(attachment.byteCount()));
      frontmatter.put("sha256", attachment.sha256());
      frontmatter.put("createdAt", attachment.createdAt().toString());
      Files.writeString(
          metadata(actor, attachment.id()),
          YamlParser.serialize(new YamlDocument(frontmatter, null)),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to store chat attachment", exception);
    }
    return attachment;
  }

  @Override
  public Optional<ChatAttachment> find(Actor actor, String attachmentId) {
    Path metadata = metadata(actor, attachmentId);
    if (!Files.isRegularFile(metadata)) {
      return Optional.empty();
    }
    try {
      return Optional.of(read(actor, YamlParser.parse(Files.readString(metadata)).frontmatter()));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read chat attachment", exception);
    }
  }

  @Override
  public List<ChatAttachment> list(Actor actor) {
    try (Stream<Path> files = Files.list(paths.directory(actor, AREA))) {
      return files
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".yaml"))
          .map(name -> name.substring(0, name.length() - ".yaml".length()))
          .flatMap(id -> find(actor, id).stream())
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to list chat attachments", exception);
    }
  }

  @Override
  public Optional<byte[]> read(Actor actor, String attachmentId) {
    try {
      Path blob = blob(actor, attachmentId);
      return Files.isRegularFile(blob) ? Optional.of(Files.readAllBytes(blob)) : Optional.empty();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read chat attachment", exception);
    }
  }

  @Override
  public void delete(Actor actor, String attachmentId) {
    try {
      Files.deleteIfExists(metadata(actor, attachmentId));
      Files.deleteIfExists(blob(actor, attachmentId));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to delete chat attachment", exception);
    }
  }

  private ChatAttachment read(Actor actor, Map<String, String> frontmatter) {
    return new ChatAttachment(
        frontmatter.get("id"),
        actor,
        frontmatter.get("displayName"),
        frontmatter.get("contentType"),
        Long.parseLong(frontmatter.get("byteCount")),
        frontmatter.get("sha256"),
        Instant.parse(frontmatter.get("createdAt")));
  }

  private Path metadata(Actor actor, String attachmentId) {
    return paths.file(actor, AREA, attachmentId + ".yaml");
  }

  private Path blob(Actor actor, String attachmentId) {
    return paths.file(actor, AREA, attachmentId + ".blob");
  }
}
