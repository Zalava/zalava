package org.zalava.skills.adapter.out.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.zalava.skills.application.port.out.SkillCatalog;
import org.zalava.skills.domain.SkillDescriptor;
import org.zalava.skills.domain.SkillProvenance;
import org.zalava.skills.domain.SkillStatus;
import org.zalava.skills.domain.SkillVisibility;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Filesystem discovery of SEA skills in the maintained {@code SKILL.md} format (YAML front matter
 * plus Markdown body). Only metadata is read here; body content is left to the maintained
 * SkillsTool runtime. Discovery is bounded and safe: unreadable, oversized or invalid candidates
 * are skipped rather than failing the listing, so a malformed file never breaks the catalogue.
 */
public final class FileSystemSkillCatalog implements SkillCatalog {

  private static final YAMLMapper YAML = new YAMLMapper();
  private static final TypeReference<Map<String, Object>> FRONT_MATTER_TYPE =
      new TypeReference<>() {};
  private static final String SKILL_FILE = "SKILL.md";
  private static final int MAXIMUM_SKILLS = 200;
  private static final int MAXIMUM_METADATA_BYTES = 64 * 1024;

  private final Path skillsDirectory;

  public FileSystemSkillCatalog(Path skillsDirectory) {
    this.skillsDirectory = skillsDirectory;
  }

  @Override
  public List<SkillDescriptor> discover() {
    if (!Files.isDirectory(skillsDirectory)) {
      return List.of();
    }
    List<SkillDescriptor> descriptors = new ArrayList<>();
    try (Stream<Path> files = Files.walk(skillsDirectory, 4)) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals(SKILL_FILE))
          .sorted()
          .limit(MAXIMUM_SKILLS)
          .map(this::descriptor)
          .flatMap(Optional::stream)
          .forEach(descriptors::add);
    } catch (IOException exception) {
      return List.of();
    }
    return List.copyOf(descriptors);
  }

  private Optional<SkillDescriptor> descriptor(Path skillFile) {
    try {
      long size = Files.size(skillFile);
      if (size <= 0 || size > MAXIMUM_METADATA_BYTES) {
        return Optional.empty();
      }
      String content = Files.readString(skillFile, StandardCharsets.UTF_8);
      Map<String, Object> frontMatter = frontMatter(content);
      if (frontMatter.isEmpty()) {
        return Optional.empty();
      }
      Path directory = skillFile.getParent();
      String directoryName = directory.getFileName().toString();
      String name = scalar(frontMatter.get("name")).orElse(directoryName);
      if (!name.equals(directoryName)) {
        return Optional.empty();
      }
      return Optional.of(
          new SkillDescriptor(
              name,
              scalar(frontMatter.get("version")).orElse(SkillDescriptor.DEFAULT_VERSION),
              scalar(frontMatter.get("description"))
                  .orElseThrow(() -> new IllegalArgumentException("description is required")),
              stringList(frontMatter.get("capabilities")),
              stringList(frontMatter.get("recommendedTools")),
              stringList(frontMatter.get("policyConstraints")),
              stringList(frontMatter.get("validationSteps")),
              scalar(frontMatter.get("seaApiVersion")).orElse(null),
              SkillVisibility.parse(scalar(frontMatter.get("visibility")).orElse(null)),
              SkillProvenance.local(relative(directory)),
              SkillStatus.INSTALLED));
    } catch (RuntimeException | IOException exception) {
      return Optional.empty();
    }
  }

  private Map<String, Object> frontMatter(String content) {
    String normalized = content.replace("\r\n", "\n");
    if (!normalized.startsWith("---")) {
      return Map.of();
    }
    int afterOpen = normalized.indexOf('\n');
    if (afterOpen < 0) {
      return Map.of();
    }
    String rest = normalized.substring(afterOpen + 1);
    int closing = closingDelimiter(rest);
    String block = closing < 0 ? rest : rest.substring(0, closing);
    if (block.isBlank()) {
      return Map.of();
    }
    Map<String, Object> parsed = YAML.readValue(block, FRONT_MATTER_TYPE);
    return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
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

  private static Optional<String> scalar(Object value) {
    if (value == null) {
      return Optional.empty();
    }
    String text = String.valueOf(value).strip();
    return text.isEmpty() ? Optional.empty() : Optional.of(text);
  }

  private static List<String> stringList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(String::valueOf)
          .map(String::strip)
          .filter(entry -> !entry.isEmpty())
          .toList();
    }
    String text = String.valueOf(value).strip();
    if (text.isEmpty()) {
      return List.of();
    }
    return List.of(text.split("\\s*,\\s*"));
  }

  private String relative(Path directory) {
    try {
      return skillsDirectory.relativize(directory).toString();
    } catch (IllegalArgumentException exception) {
      return directory.getFileName().toString();
    }
  }
}
