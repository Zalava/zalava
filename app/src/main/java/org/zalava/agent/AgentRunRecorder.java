package org.zalava.agent;

import java.util.List;

public interface AgentRunRecorder {

  void record(AgentRunRecord record);

  List<AgentRunRecord> recent();
}
