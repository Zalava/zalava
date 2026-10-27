package org.zalava.capabilities.operation.adapter.out.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.InvocationContext;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.MemberProviderCapabilityPolicy;
import org.zalava.modules.runtime.LoadedZalavaProvider;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

class ZalavaRuntimeProviderCatalogTest {
  @Test
  void hostEstablishesProvenanceAndPreservesProviderOperations() {
    var provider = provider("module", List.of("read-only"));
    var catalog = catalog(provider, "module");
    var hosted = catalog.findProvider("provider").orElseThrow();
    assertThat(hosted.descriptor().policyTags()).contains("zalava_backed", "read-only");
    var tool = new ZalavaToolDescriptor("read", "Read", false, List.of());
    assertThat(MemberProviderCapabilityPolicy.eligible(hosted, tool)).isTrue();
    when(provider.listTools()).thenReturn(List.of(tool));
    assertThat(hosted.listTools()).containsExactly(tool);
    assertThat(hosted.capabilities()).isEqualTo(ProviderCapabilities.toolsOnly());
    var context = new InvocationContext("actor", false, Map.of());
    var arguments = Map.<String, Object>of("value", 1);
    var result = ZalavaOperationResult.success(Map.of("ok", true));
    when(provider.callTool("read", arguments, context)).thenReturn(result);
    assertThat(hosted.callTool("read", arguments, context)).isSameAs(result);
    hosted.listResources();
    verify(provider).listResources();
    hosted.readResource("resource", context);
    verify(provider).readResource("resource", context);
    hosted.listPrompts();
    verify(provider).listPrompts();
    hosted.resolvePrompt("prompt", arguments, context);
    verify(provider).resolvePrompt("prompt", arguments, context);
    assertThat(catalog.findProvider("missing")).isEmpty();
    assertThat(
            MemberProviderCapabilityPolicy.eligible(
                hosted, new ZalavaToolDescriptor("shell", "Shell", false, List.of("shell"))))
        .isFalse();
  }

  @Test
  void mismatchedProviderCannotSupplyItsOwnHostMarker() {
    var hosted =
        catalog(provider("different-module", List.of("zalava_backed", "read-only")), "module")
            .providers()
            .getFirst();
    assertThat(hosted.descriptor().policyTags()).doesNotContain("zalava_backed");
    assertThat(
            MemberProviderCapabilityPolicy.eligible(
                hosted, new ZalavaToolDescriptor("read", "Read", false, List.of())))
        .isFalse();
  }

  private static ZalavaProvider provider(String module, List<String> tags) {
    var provider = mock(ZalavaProvider.class);
    when(provider.capabilities()).thenReturn(ProviderCapabilities.toolsOnly());
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "provider",
                module,
                "test",
                "Provider",
                "Description",
                "1",
                ProviderCapabilities.toolsOnly(),
                tags,
                Map.of("owner", "self")));
    return provider;
  }

  private static ZalavaRuntimeProviderCatalog catalog(ZalavaProvider provider, String module) {
    var runtime = mock(RuntimeQueries.class);
    when(runtime.loadedProviders())
        .thenReturn(
            List.of(
                new LoadedZalavaProvider(
                    new ModuleDescriptor(module, "1", "Module", "Description"),
                    new ProviderFactoryDescriptor(
                        "factory", module, "test", "Factory", "Description"),
                    provider)));
    return new ZalavaRuntimeProviderCatalog(runtime);
  }
}
