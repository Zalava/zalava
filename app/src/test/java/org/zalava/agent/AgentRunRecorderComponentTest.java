package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.zalava.agent.adapter.out.filesystem.FileSystemAgentRunRecorder;
import org.zalava.agent.application.port.out.AgentRunStore;
import org.zalava.support.SeaComponentTest;

@SeaComponentTest
class AgentRunRecorderComponentTest {

  @Autowired private AgentRunStore runStore;

  @Test
  void usesFilesystemRecorderAsApplicationRecorder() {
    assertThat(runStore).isInstanceOf(FileSystemAgentRunRecorder.class);
  }
}
