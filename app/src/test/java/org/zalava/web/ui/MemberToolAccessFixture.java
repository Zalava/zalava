package org.zalava.web.ui;

import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.zalava.api.*;

@TestConfiguration(proxyBeanMethods = false)
public class MemberToolAccessFixture {
  public static final String MODULE = "member-access-fixture";
  public static final String PROVIDER = "member-clock";

  @Bean
  ZalavaModule memberAccessFixtureModule() {
    return new ZalavaModule() {
      public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            MODULE, "1.0.0", "Member clock", "Permission acceptance fixture");
      }

      public List<ProviderFactory> providerFactories() {
        return List.of(
            new ProviderFactory() {
              public ProviderFactoryDescriptor descriptor() {
                return new ProviderFactoryDescriptor(
                    "clock", MODULE, "time", "Member clock", "Time fixture");
              }

              public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
                return List.of(
                    new ZalavaProvider() {
                      public ProviderDescriptor descriptor() {
                        return new ProviderDescriptor(
                            PROVIDER,
                            MODULE,
                            "time",
                            "Member clock",
                            "Read-only time fixture",
                            "1.0.0",
                            capabilities(),
                            List.of("read-only"),
                            Map.of("clock", "system"));
                      }

                      public ProviderCapabilities capabilities() {
                        return ProviderCapabilities.toolsOnly();
                      }

                      public List<ZalavaToolDescriptor> listTools() {
                        return List.of(
                            new ZalavaToolDescriptor(
                                "current_time", "Read time", false, List.of("read-only")),
                            new ZalavaToolDescriptor(
                                "convert_time", "Convert time", false, List.of("read-only")),
                            new ZalavaToolDescriptor(
                                "unsafe_shell", "Restricted operation", false, List.of("shell")),
                            new ZalavaToolDescriptor(
                                "write_time", "Side effect fixture", true, List.of()));
                      }

                      public ZalavaOperationResult callTool(
                          String tool, Map<String, Object> args, InvocationContext invocation) {
                        return ZalavaOperationResult.success(
                            Map.of("time", "2026-10-26T12:00:00Z"));
                      }
                    });
              }
            });
      }
    };
  }
}
