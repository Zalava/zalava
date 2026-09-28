package org.zalava.tasks.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public record TaskReference(LocalDate date, String filename) {

  private static final Pattern TASK_FILENAME = Pattern.compile("\\d{6}-[a-zA-Z0-9._-]*\\.md");

  public TaskReference {
    if (date == null || filename == null || !TASK_FILENAME.matcher(filename).matches()) {
      throw new IllegalArgumentException("Invalid task reference");
    }
  }

  public static TaskReference parse(String date, String filename) {
    try {
      return new TaskReference(LocalDate.parse(date), filename);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Invalid task reference", ex);
    }
  }

  public String path() {
    return date + "/" + filename;
  }
}
