package org.zalava.agent;

import java.util.List;

public interface AgentContextAssembler {

  AgentContextAssembly assemble(String input);

  AgentContextAssembly assemble(String input, List<AgentRequestTools.ToolSummary> toolSummaries);

  AgentContextAssembly assemble(String input, AgentRequestTools.RequestToolSelection toolSelection);
}
