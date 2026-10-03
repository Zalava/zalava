package org.zalava.assistant.agent.adapter.out.springai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/** Adapts every tool passed to a model to the provider-safe function-name contract. */
final class ModelSafeToolCallbacks {
  private static final Pattern MODEL_SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
  private static final int MAX_READABLE_LENGTH = 36;

  private ModelSafeToolCallbacks() {}

  static List<ToolCallback> forTools(List<Object> tools) {
    List<ToolCallback> callbacks = new ArrayList<>();
    for (Object tool : tools) {
      if (tool instanceof ToolCallback callback) {
        callbacks.add(modelSafe(callback));
      } else if (tool instanceof ToolCallbackProvider provider) {
        for (ToolCallback callback : provider.getToolCallbacks()) {
          callbacks.add(modelSafe(callback));
        }
      } else {
        for (ToolCallback callback :
            MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks()) {
          callbacks.add(modelSafe(callback));
        }
      }
    }
    return callbacks;
  }

  private static ToolCallback modelSafe(ToolCallback callback) {
    ToolDefinition definition = callback.getToolDefinition();
    if (MODEL_SAFE_NAME.matcher(definition.name()).matches()) {
      return callback;
    }
    return new RenamedToolCallback(callback, modelSafeName(definition.name()));
  }

  private static String modelSafeName(String originalName) {
    String readableName =
        originalName == null ? "tool" : originalName.replaceAll("[^A-Za-z0-9_-]", "_");
    if (readableName.isBlank()) {
      readableName = "tool";
    }
    if (readableName.length() > MAX_READABLE_LENGTH) {
      readableName = readableName.substring(0, MAX_READABLE_LENGTH);
    }
    return "zalava_" + readableName + "_" + hash(originalName == null ? "" : originalName);
  }

  private static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  private static final class RenamedToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final ToolDefinition definition;

    private RenamedToolCallback(ToolCallback delegate, String modelSafeName) {
      this.delegate = delegate;
      ToolDefinition original = delegate.getToolDefinition();
      this.definition =
          ToolDefinition.builder()
              .name(modelSafeName)
              .description(original.description())
              .inputSchema(original.inputSchema())
              .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
      return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
      return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
      return delegate.call(toolInput, toolContext);
    }
  }
}
