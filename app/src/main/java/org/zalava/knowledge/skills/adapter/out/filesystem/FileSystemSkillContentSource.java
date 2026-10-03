package org.zalava.knowledge.skills.adapter.out.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import org.zalava.knowledge.skills.application.port.out.SkillContentSource;

/**
 * Loads the Markdown body of a maintained {@code SKILL.md} for Zalava-owned activation.
 *
 * <p>Only a single kebab-case skill directory below the configured skills root is read, front
 * matter is stripped, and oversized or unreadable files yield no content instead of failing. The
 * body is returned verbatim; Zalava policy validates it before anything is activated.
 */
public final class FileSystemSkillContentSource implements SkillContentSource {

  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final String SKILL_FILE = "SKILL.md";
  private static final int MAXIMUM_BYTES = 64 * 1024;

  private final Path skillsDirectory;

  public FileSystemSkillContentSource(Path skillsDirectory) {
    this.skillsDirectory = skillsDirectory.toAbsolutePath().normalize();
  }

  @Override
  public Optional<String> load(String name) {
    if (name == null || !KEBAB_CASE.matcher(name).matches()) {
      return Optional.empty();
    }
    Path skillFile = skillsDirectory.resolve(name).resolve(SKILL_FILE).normalize();
    if (!skillFile.startsWith(skillsDirectory) || !Files.isRegularFile(skillFile)) {
      return Optional.empty();
    }
    try {
      if (Files.size(skillFile) > MAXIMUM_BYTES) {
        return Optional.empty();
      }
      String body = body(Files.readString(skillFile, StandardCharsets.UTF_8));
      return body.isBlank() ? Optional.empty() : Optional.of(body);
    } catch (IOException | RuntimeException exception) {
      return Optional.empty();
    }
  }

  static String body(String content) {
    String normalized = content.replace("\r\n", "\n");
    if (!normalized.startsWith("---")) {
      return normalized.strip();
    }
    int afterOpen = normalized.indexOf('\n');
    if (afterOpen < 0) {
      return "";
    }
    String rest = normalized.substring(afterOpen + 1);
    int closing = closingDelimiter(rest);
    if (closing < 0) {
      return rest.strip();
    }
    int lineEnd = rest.indexOf('\n', closing);
    return lineEnd < 0 ? "" : rest.substring(lineEnd + 1).strip();
  }

  private static int closingDelimiter(String text) {
    int index = 0;
    while (index < text.length()) {
      if (text.startsWith("---", index)
          && (index == 0 || text.charAt(index - 1) == '\n')
          && (index + 3 >= text.length() || text.charAt(index + 3) == '\n')) {
        return index;
      }
      int next = text.indexOf('\n', index);
      if (next < 0) {
        return -1;
      }
      index = next + 1;
    }
    return -1;
  }
}
