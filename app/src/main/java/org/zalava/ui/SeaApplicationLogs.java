package org.zalava.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded, read-only view of SEA's configured application log and its rotations. */
@Component
public final class SeaApplicationLogs {
  private static final int MAX_LINES = 200;
  private static final int MAX_LINE_LENGTH = 4096;
  private static final int MAX_FILES = 8;
  private static final long MAX_SOURCE_BYTES = 60L * 1024 * 1024;
  private static final Pattern SECRET_ASSIGNMENT =
      Pattern.compile(
          "(?i)(\\b(?:password|secret|token|api[_-]?key)\\b\\s*[:=]\\s*)[^\\s,;]+",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+");

  private final Path logFile;

  public SeaApplicationLogs(@Value("${logging.file.name:}") String configuredLogFile) {
    this.logFile =
        configuredLogFile.isBlank()
            ? null
            : Path.of(configuredLogFile).toAbsolutePath().normalize();
  }

  public Snapshot latest() {
    if (logFile == null || !Files.isRegularFile(logFile, LinkOption.NOFOLLOW_LINKS)) {
      return new Snapshot(
          false, List.of(), "SEA application logs are unavailable for this launch.");
    }
    try {
      Path directory = logFile.getParent();
      String prefix = logFile.getFileName().toString() + ".";
      List<Path> archives;
      try (var files = Files.list(directory)) {
        archives =
            files
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> path.getFileName().toString().endsWith(".gz"))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparingLong(SeaApplicationLogs::modifiedAt).reversed())
                .limit(MAX_FILES - 1)
                .toList();
      }
      List<Path> sources = new ArrayList<>();
      sources.addAll(archives.reversed());
      sources.add(logFile);
      ArrayDeque<String> tail = new ArrayDeque<>();
      long sourceBytes = 0;
      for (Path source : sources) {
        sourceBytes += Files.size(source);
        if (sourceBytes > MAX_SOURCE_BYTES) break;
        try (InputStream input =
                source.getFileName().toString().endsWith(".gz")
                    ? new GZIPInputStream(Files.newInputStream(source))
                    : Files.newInputStream(source);
            BufferedReader lines =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
          String line;
          while ((line = lines.readLine()) != null) {
            String safe =
                redact(
                    line.length() > MAX_LINE_LENGTH
                        ? line.substring(0, MAX_LINE_LENGTH) + "…"
                        : line);
            tail.addLast(safe);
            if (tail.size() > MAX_LINES) tail.removeFirst();
          }
        }
      }
      return new Snapshot(true, List.copyOf(tail), "");
    } catch (IOException exception) {
      return new Snapshot(false, List.of(), "SEA application logs could not be read.");
    }
  }

  private static long modifiedAt(Path path) {
    try {
      return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
    } catch (IOException exception) {
      return Long.MIN_VALUE;
    }
  }

  private static String redact(String line) {
    return BEARER
        .matcher(SECRET_ASSIGNMENT.matcher(line).replaceAll("$1[REDACTED]"))
        .replaceAll("Bearer [REDACTED]");
  }

  public record Snapshot(boolean available, List<String> lines, String message) {}
}
