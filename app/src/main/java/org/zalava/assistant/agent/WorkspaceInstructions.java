package org.zalava.assistant.agent;

import static org.zalava.ZalavaConfiguration.AGENT_MD;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Shared persisted instructions for setup, settings and subsequent model requests. */
@Component
public final class WorkspaceInstructions {
  private final Resource workspace;

  public WorkspaceInstructions(@Value("${agent.workspace}") Resource workspace) {
    this.workspace = workspace;
  }

  public String defaults() {
    try {
      return new ClassPathResource("assistant/workspace-instructions.txt")
          .getContentAsString(StandardCharsets.UTF_8)
          .strip();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load default workspace instructions", exception);
    }
  }

  public String current() {
    try {
      for (String name : new String[] {AGENT_MD, "AGENT.md"}) {
        Resource file = workspace.createRelative(name);
        if (file.exists()) {
          String text = file.getContentAsString(StandardCharsets.UTF_8);
          if (!text.isBlank()) return text;
        }
      }
      return defaults();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read workspace instructions", exception);
    }
  }

  public boolean customized() {
    return !current().strip().equals(defaults());
  }

  public void save(String instructions) {
    if (instructions == null || instructions.isBlank()) {
      throw new IllegalArgumentException("Workspace instructions cannot be empty.");
    }
    try {
      Files.writeString(
          workspace.createRelative(AGENT_MD).getFilePath(),
          instructions.strip() + System.lineSeparator(),
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to update workspace instructions", exception);
    }
  }

  public void reset() {
    // Persist the default override so a legacy AGENT.md cannot reappear after reset.
    save(defaults());
  }
}
