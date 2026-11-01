package org.zalava.capabilities.operation.application;

import java.util.List;
import java.util.Map;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;

/** Host callbacks that can be confirmed before the original callback runs. */
public final class HostCallbackCatalog {
  public static final String MODULE = "zalava-host";
  public static final String PROVIDER = "zalava-internal";
  private static final List<ZalavaToolDescriptor> TOOLS =
      List.of(
          new ZalavaToolDescriptor(
              "proposeMemory", "Propose a memory for the owner to review.", true),
          new ZalavaToolDescriptor("requestClarification", "Ask the job owner a question.", true),
          new ZalavaToolDescriptor(
              "recommendModuleDevelopment",
              "Find modules or recommend a development request.",
              false),
          new ZalavaToolDescriptor(
              "createModuleDevelopmentRequest", "Create a module development request.", true),
          new ZalavaToolDescriptor(
              "reviseModuleDevelopmentRequest", "Revise a module development request.", true),
          new ZalavaToolDescriptor(
              "exportModuleDevelopmentWorkspace", "Export a development workspace.", true),
          new ZalavaToolDescriptor(
              "beginModuleDevelopment", "Record the start of module development.", true),
          new ZalavaToolDescriptor(
              "submitModuleDevelopmentCandidate",
              "Submit a module candidate for evaluation.",
              true),
          new ZalavaToolDescriptor(
              "inspectModuleDevelopmentRequest", "Inspect a module development request.", false));
  private static final ZalavaProvider PROVIDER_INSTANCE =
      new ZalavaProvider() {
        @Override
        public ProviderDescriptor descriptor() {
          return new ProviderDescriptor(
              PROVIDER,
              MODULE,
              "host",
              "Zalava",
              "Built-in actions",
              "1",
              ProviderCapabilities.toolsOnly(),
              List.of(),
              Map.of());
        }

        @Override
        public ProviderCapabilities capabilities() {
          return ProviderCapabilities.toolsOnly();
        }

        @Override
        public List<ZalavaToolDescriptor> listTools() {
          return TOOLS;
        }
      };

  private HostCallbackCatalog() {}

  public static ZalavaProvider provider() {
    return PROVIDER_INSTANCE;
  }

  public static boolean contains(ToolKey key) {
    return MODULE.equals(key.moduleId())
        && PROVIDER.equals(key.providerId())
        && tool(key.toolName()) != null;
  }

  public static ZalavaToolDescriptor tool(String name) {
    return TOOLS.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
  }
}
