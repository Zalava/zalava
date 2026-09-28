package org.zalava.agent.adapter.out.policy;

import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.tools.ActorTaskCreationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreamingToolContextBridgeConfiguration {

  @Bean
  public StreamingToolContextBridge streamingToolContextBridge(
      ActorExecutionContext actorExecutionContext,
      ActorTaskCreationContext actorTaskCreationContext) {
    return new StreamingToolContextBridge(actorExecutionContext, actorTaskCreationContext);
  }
}
