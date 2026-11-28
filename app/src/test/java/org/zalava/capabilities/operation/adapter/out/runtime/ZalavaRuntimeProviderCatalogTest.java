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
  void hostGenerationIsNeutralStableAndNotProviderSupplied() {
    var first = provider("first-module", List.of());
    var second = provider("second-module", List.of());
    var runtime = mock(RuntimeQueries.class);
    var firstModule = new ModuleDescriptor("first-module", "1", "First", "Description");
    var secondModule = new ModuleDescriptor("second-module", "1", "Second", "Description");
    var firstFactory =
        new ProviderFactoryDescriptor("f1", "first-module", "test", "First", "Description");
    var secondFactory =
        new ProviderFactoryDescriptor("f2", "second-module", "test", "Second", "Description");
    var firstLoaded = new LoadedZalavaProvider(firstModule, firstFactory, first);
    var secondLoaded = new LoadedZalavaProvider(secondModule, secondFactory, second);
    when(runtime.loadedProviders()).thenReturn(List.of(firstLoaded, secondLoaded));
    var catalog = new ZalavaRuntimeProviderCatalog(runtime);
    var hosted = catalog.providers();
    var firstGeneration = catalog.generationOf(hosted.getFirst()).orElseThrow();
    var secondGeneration = catalog.generationOf(hosted.getLast()).orElseThrow();
    assertThat(firstGeneration).isNotEqualTo(secondGeneration);
    assertThat(catalog.generationOf(catalog.providers().getFirst())).contains(firstGeneration);
    assertThat(catalog.generationOf(first)).isEmpty();
    assertThat(hosted.getFirst().descriptor().scope())
        .containsExactlyEntriesOf(Map.of("owner", "self"));
    var otherHost = new ZalavaRuntimeProviderCatalog(runtime);
    assertThat(otherHost.generationOf(hosted.getFirst())).isEmpty();
    assertThat(otherHost.generationOf(otherHost.providers().getFirst()).orElseThrow())
        .isNotEqualTo(firstGeneration);

    when(runtime.loadedProviders()).thenReturn(List.of(secondLoaded));
    assertThat(catalog.generationOf(hosted.getFirst())).isEmpty();
    when(runtime.loadedProviders()).thenReturn(List.of(firstLoaded, secondLoaded));
    assertThat(catalog.generationOf(catalog.providers().getFirst()).orElseThrow())
        .isNotEqualTo(firstGeneration);
    var beforeReplacement = catalog.providers().getFirst();
    var previous = catalog.generationOf(beforeReplacement).orElseThrow();
    var replacement = provider("first-module", List.of());
    when(runtime.loadedProviders())
        .thenReturn(
            List.of(
                new LoadedZalavaProvider(firstModule, firstFactory, replacement), secondLoaded));
    assertThat(catalog.generationOf(beforeReplacement)).isEmpty();
    assertThat(catalog.generationOf(catalog.providers().getFirst()).orElseThrow())
        .isNotEqualTo(previous);
  }

  @Test
  void generationDoesNotTurnAnEmptyScopeIntoMemberEligibility() {
    var provider = provider("empty-module", List.of());
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "provider",
                "empty-module",
                "test",
                "Provider",
                "Description",
                "1",
                ProviderCapabilities.toolsOnly(),
                List.of(),
                Map.of()));
    var catalog = catalog(provider, "empty-module");
    var hosted = catalog.providers().getFirst();
    assertThat(catalog.generationOf(hosted)).isPresent();
    assertThat(hosted.descriptor().scope()).isEmpty();
    assertThat(
            MemberProviderCapabilityPolicy.eligible(
                hosted, new ZalavaToolDescriptor("write", "Write", true)))
        .isFalse();
    var mismatched = catalog(provider, "different-module");
    assertThat(mismatched.generationOf(mismatched.providers().getFirst())).isEmpty();
  }

  @Test
  void declaredScopeCannotSupplyTheHostGeneration() {
    var provider = provider("fixture-module", List.of());
    String claimed = "00000000-0000-0000-0000-000000000001";
    when(provider.descriptor())
        .thenReturn(
            new ProviderDescriptor(
                "provider",
                "fixture-module",
                "test",
                "Provider",
                "Description",
                "1",
                ProviderCapabilities.toolsOnly(),
                List.of(),
                Map.of("zalavaProviderGeneration", claimed)));
    var catalog = catalog(provider, "fixture-module");
    var hosted = catalog.providers().getFirst();
    assertThat(hosted.descriptor().scope()).containsEntry("zalavaProviderGeneration", claimed);
    assertThat(catalog.generationOf(hosted).orElseThrow().toString()).isNotEqualTo(claimed);
  }

  @Test
  void homeAssistantScopeTracksTheActualLoadedProviderGeneration() {
    var first = provider("zalava-module-home-assistant", List.of());
    var second = provider("zalava-module-home-assistant", List.of());
    var runtime = mock(RuntimeQueries.class);
    var module = new ModuleDescriptor("zalava-module-home-assistant", "1", "Module", "Description");
    var factory =
        new ProviderFactoryDescriptor(
            "factory", module.moduleId(), "test", "Factory", "Description");
    when(runtime.loadedProviders())
        .thenReturn(List.of(new LoadedZalavaProvider(module, factory, first)));
    var catalog = new ZalavaRuntimeProviderCatalog(runtime);
    var original = catalog.providers().getFirst().descriptor().scope();
    assertThat(original).containsKey("zalavaProviderGeneration");
    assertThat(catalog.providers().getFirst().descriptor().scope()).isEqualTo(original);
    when(runtime.loadedProviders())
        .thenReturn(List.of(new LoadedZalavaProvider(module, factory, second)));
    assertThat(catalog.providers().getFirst().descriptor().scope()).isNotEqualTo(original);
    assertThat(
            new ZalavaRuntimeProviderCatalog(runtime).providers().getFirst().descriptor().scope())
        .isNotEqualTo(original);
  }

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
