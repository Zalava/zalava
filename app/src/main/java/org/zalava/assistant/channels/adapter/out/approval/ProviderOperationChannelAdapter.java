package org.zalava.assistant.channels.adapter.out.approval;

import org.zalava.assistant.channels.application.ChannelProviderOperationException;
import org.zalava.assistant.channels.application.port.out.ChannelProviderOperations;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;

public final class ProviderOperationChannelAdapter implements ChannelProviderOperations {
  private final ProviderToolOperations operations;

  public ProviderOperationChannelAdapter(ProviderToolOperations operations) {
    this.operations = operations;
  }

  @Override
  public void allowUnscoped(String requestId) {
    invoke(() -> operations.allowUnscoped(requestId));
  }

  @Override
  public void allowUnscopedTool(String requestId) {
    invoke(() -> operations.allowUnscopedTool(requestId));
  }

  @Override
  public void denyUnscoped(String requestId) {
    invoke(() -> operations.denyUnscoped(requestId));
  }

  private static void invoke(Runnable invocation) {
    try {
      invocation.run();
    } catch (ProviderToolOperationException ex) {
      ChannelProviderOperationException.Code code =
          switch (ex.code()) {
            case APPROVAL_NOT_FOUND -> ChannelProviderOperationException.Code.APPROVAL_NOT_FOUND;
            case APPROVAL_CONFLICT -> ChannelProviderOperationException.Code.APPROVAL_CONFLICT;
            default -> ChannelProviderOperationException.Code.OTHER;
          };
      throw new ChannelProviderOperationException(code, ex.getMessage());
    }
  }
}
