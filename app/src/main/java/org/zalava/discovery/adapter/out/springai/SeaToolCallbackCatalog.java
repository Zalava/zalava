package org.zalava.discovery.adapter.out.springai;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.core.ParameterizedTypeReference;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.operation.adapter.in.agent.SeaProviderToolInvoker;
import org.zalava.operation.application.port.out.ProviderCatalog;
import tools.jackson.databind.ObjectMapper;

public final class SeaToolCallbackCatalog {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ParameterizedTypeReference<Map<String, Object>> ARGUMENTS_TYPE =
      new ParameterizedTypeReference<>() {};

  private final ProviderCatalog providerCatalog;
  private final SeaProviderToolInvoker invoker;

  public SeaToolCallbackCatalog(ProviderCatalog providerCatalog, SeaProviderToolInvoker invoker) {
    this.providerCatalog = providerCatalog;
    this.invoker = invoker;
  }

  public List<Entry> entries() {
    return providerCatalog.providers().stream()
        .filter(SeaToolCallbackCatalog::supportsSeaNativeTools)
        .flatMap(
            provider ->
                provider.listTools().stream()
                    .map(tool -> entry(provider.descriptor(), tool, invoker)))
        .toList();
  }

  public ToolCallback callback(String providerId, String toolName) {
    return entries().stream()
        .filter(entry -> entry.providerId().equals(providerId) && entry.toolName().equals(toolName))
        .map(Entry::callback)
        .findFirst()
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "SEA tool callback not found: " + providerId + "/" + toolName));
  }

  private static boolean supportsSeaNativeTools(SeaProvider provider) {
    return provider.capabilities().supportsTools();
  }

  private static Entry entry(
      ProviderDescriptor provider, SeaToolDescriptor tool, SeaProviderToolInvoker invoker) {
    String callbackName = SeaToolCallbackNames.forTool(provider.providerId(), tool.name());
    ToolCallback callback =
        FunctionToolCallback.<Map<String, Object>, Object>builder(
                callbackName,
                (arguments, context) ->
                    invoker.invoke(
                        provider.providerId(), tool.name(), json(arguments), "spring-ai-callback"))
            .description(provider.displayName() + ": " + tool.description())
            .inputType(ARGUMENTS_TYPE)
            .inputSchema(json(tool.inputSchema()))
            .toolCallResultConverter((result, returnType) -> json(result))
            .build();
    ToolReference reference = SeaToolReferences.from(provider, tool);
    return new Entry(provider.providerId(), tool.name(), callback, reference);
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to serialize SEA tool callback value", ex);
    }
  }

  public record Entry(
      String providerId, String toolName, ToolCallback callback, ToolReference reference) {}
}
