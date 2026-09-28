package org.zalava.onboarding.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class S4_AgentMdStepTest {

  @TempDir Path root;

  private record StepContext(S4_AgentMdStep step, Path workspace) {}

  private StepContext newStep() throws IOException {
    // A unique workspace per test isolates the persisted AGENT.private.md file.
    Path workspace = Files.createDirectories(root.resolve("ws-" + System.nanoTime()));
    // Production configures agent.workspace as a directory URI with a trailing slash
    // (file:./workspace/), so S4 resolves relative files against the directory itself.
    return new StepContext(
        new S4_AgentMdStep(new org.springframework.core.io.UrlResource(workspace.toUri() + "/")),
        workspace);
  }

  @Test
  void exposesStepIdentity() throws IOException {
    StepContext context = newStep();

    assertThat(context.step().getStepId()).isEqualTo("agent");
    assertThat(context.step().getStepTitle()).isEqualTo("AGENT.md");
    assertThat(context.step().getTemplatePath()).isEqualTo("onboarding/steps/S4-agent");
  }

  @Test
  void prepareModelReadsThePrivateAgentFileWhenPresent() throws IOException {
    StepContext context = newStep();
    Files.writeString(context.workspace().resolve("AGENT.private.md"), "Private instructions.");

    Map<String, Object> model = new HashMap<>();
    context.step().prepareModel(new HashMap<>(), model);

    assertThat(model.get("agentContent")).isEqualTo("Private instructions.");
  }

  @Test
  void prepareModelFallsBackToTheSharedAgentFileAndThenToEmpty() throws IOException {
    StepContext shared = newStep();
    Files.writeString(shared.workspace().resolve("AGENT.md"), "Shared instructions.");
    Map<String, Object> model = new HashMap<>();
    shared.step().prepareModel(new HashMap<>(), model);
    assertThat(model.get("agentContent")).isEqualTo("Shared instructions.");

    StepContext empty = newStep();
    Map<String, Object> emptyModel = new HashMap<>();
    empty.step().prepareModel(new HashMap<>(), emptyModel);
    assertThat(emptyModel.get("agentContent")).isEqualTo("");
  }

  @Test
  void prepareModelReusesContentAlreadyCollectedInTheSession() throws IOException {
    StepContext context = newStep();
    Map<String, Object> session = new HashMap<>();
    session.put(S4_AgentMdStep.SESSION_AGENT_CONTENT, "Session draft.");
    Map<String, Object> model = new HashMap<>();
    Files.writeString(context.workspace().resolve("AGENT.md"), "Shared instructions.");

    context.step().prepareModel(session, model);

    assertThat(model.get("agentContent")).isEqualTo("Session draft.");
  }

  @Test
  void processStepRejectsBlankInstructions() throws IOException {
    StepContext context = newStep();
    Map<String, Object> session = new HashMap<>();

    assertThat(context.step().processStep(Map.of("agentContent", "   "), session))
        .isEqualTo("The AGENT.md instructions cannot be empty.");

    assertThat(session).isEmpty();
  }

  @Test
  void processStepStoresNonBlankInstructions() throws IOException {
    StepContext context = newStep();
    Map<String, Object> session = new HashMap<>();

    assertThat(context.step().processStep(Map.of("agentContent", "Be careful."), session)).isNull();

    assertThat(session.get(S4_AgentMdStep.SESSION_AGENT_CONTENT)).isEqualTo("Be careful.");
  }

  @Test
  void saveConfigurationWritesThePrivateAgentFile() throws Exception {
    StepContext context = newStep();
    Map<String, Object> session = new HashMap<>();
    session.put(S4_AgentMdStep.SESSION_AGENT_CONTENT, "Saved instructions.");

    context
        .step()
        .saveConfiguration(
            session,
            org.mockito.Mockito.mock(
                org.zalava.configuration.application.port.in.ConfigurationCommands.class));

    assertThat(context.workspace().resolve("AGENT.private.md"))
        .exists()
        .hasContent("Saved instructions.");
  }

  @Test
  void saveConfigurationWithoutContentWritesNothing() throws Exception {
    StepContext context = newStep();

    context
        .step()
        .saveConfiguration(
            new HashMap<>(),
            org.mockito.Mockito.mock(
                org.zalava.configuration.application.port.in.ConfigurationCommands.class));

    assertThat(context.workspace().resolve("AGENT.private.md")).doesNotExist();
  }

  @Test
  void saveConfigurationSurfacesWriteFailures() throws IOException {
    // A directory where the file should be forces the write to fail.
    StepContext context = newStep();
    Files.createDirectories(context.workspace().resolve("AGENT.private.md"));
    Map<String, Object> session = new HashMap<>();
    session.put(S4_AgentMdStep.SESSION_AGENT_CONTENT, "Saved instructions.");

    assertThatThrownBy(
            () ->
                context
                    .step()
                    .saveConfiguration(
                        session,
                        org.mockito.Mockito.mock(
                            org.zalava.configuration.application.port.in.ConfigurationCommands
                                .class)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to write AGENT.private.md");
  }
}
