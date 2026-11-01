package org.zalava.capabilities.operation.application;

import java.util.List;
import java.util.Map;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;

/** Host-owned read tools with explicit confirmation support. */
public final class HostReadToolCatalog {
  public static final String MODULE = "zalava-host";
  public static final String PROVIDER = "zalava-knowledge";
  private static final List<ZalavaToolDescriptor> TOOLS =
      List.of(
          new ZalavaToolDescriptor(
              "knowledge.search", "Search documents the person can access.", false),
          new ZalavaToolDescriptor(
              "knowledge.get-source", "Read a document the person can access.", false));
  private static final ZalavaProvider PROVIDER_INSTANCE =
      new ZalavaProvider() {
        @Override
        public ProviderDescriptor descriptor() {
          return new ProviderDescriptor(
              PROVIDER,
              MODULE,
              "host",
              "Knowledge",
              "Built-in document reads",
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

  private HostReadToolCatalog() {}

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
