package org.zalava.agent.adapter.out.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.tools.ActorTaskCreationContext;

@Configuration
public class StreamingToolContextBridgeConfiguration {

  @Bean
  public StreamingToolContextBridge streamingToolContextBridge(
      ActorExecutionContext actorExecutionContext,
      ActorTaskCreationContext actorTaskCreationContext) {
    return new StreamingToolContextBridge(actorExecutionContext, actorTaskCreationContext);
  }
}
