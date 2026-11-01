package org.zalava.assistant.agent.adapter.out.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests;
import org.zalava.capabilities.operation.adapter.out.approval.HostCallbackChatPolicy;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.tasks.capture.ActorTaskCreationContext;
import org.zalava.tasks.domain.TaskExecutionContext;

@Configuration
public class StreamingToolContextBridgeConfiguration {

  @Bean
  public StreamingToolContextBridge streamingToolContextBridge(
      ActorExecutionContext actorExecutionContext,
      ActorTaskCreationContext actorTaskCreationContext,
      TaskExecutionContext taskExecutionContext,
      ChatToolConfirmation confirmation,
      ZalavaToolApprovalRequests approvals) {
    return new StreamingToolContextBridge(
        actorExecutionContext,
        actorTaskCreationContext,
        new HostCallbackChatPolicy(
            actorExecutionContext, taskExecutionContext, confirmation, approvals));
  }
}
