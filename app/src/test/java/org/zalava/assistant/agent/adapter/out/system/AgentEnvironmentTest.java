package org.zalava.assistant.agent.adapter.out.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers the deterministic environment summary surfaced to provider boundaries. */
class AgentEnvironmentTest {

  @Test
  void rendersWorkingDirectoryPlatformAndTimezoneEvidence() {
    String summary = AgentEnvironment.info().toString();

    assertThat(summary)
        .contains("Working directory: " + System.getProperty("user.dir"))
        .contains("Is directory a git repo: ")
        .contains("Platform: " + System.getProperty("os.name").toLowerCase())
        .contains(
            "OS Version: " + System.getProperty("os.name") + " " + System.getProperty("os.version"))
        .contains("Timezone: " + System.getProperty("user.timezone"))
        .contains("Current time: ");
    assertThat(AgentEnvironment.ENVIRONMENT_INFO_KEY).isEqualTo("ENVIRONMENT_INFO");
  }
}
