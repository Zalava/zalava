package org.zalava.runtime.adapter.in.http;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.ModuleDescriptor;
import org.zalava.PromptDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.runtime.LoadedSeaProvider;
import org.zalava.runtime.SeaRuntime;

@RestController
@RequestMapping("/api/sea")
@Profile({"dev", "test"})
public class SeaRuntimeAdminController {

  private final SeaRuntime seaRuntime;

  public SeaRuntimeAdminController(SeaRuntime seaRuntime) {
    this.seaRuntime = seaRuntime;
  }

  @GetMapping("/modules")
  public List<ModuleResponse> modules() {
    return seaRuntime.modules().stream()
        .map(module -> ModuleResponse.from(module.descriptor()))
        .toList();
  }

  @GetMapping("/providers")
  public List<LoadedProviderResponse> providers() {
    return seaRuntime.loadedProviders().stream().map(LoadedProviderResponse::from).toList();
  }

  @GetMapping("/providers/{providerId}")
  public LoadedProviderResponse provider(@PathVariable String providerId) {
    return LoadedProviderResponse.from(findLoadedProvider(providerId));
  }

  @GetMapping("/providers/{providerId}/tools")
  public List<ToolResponse> tools(@PathVariable String providerId) {
    return findProvider(providerId).listTools().stream().map(ToolResponse::from).toList();
  }

  @GetMapping("/providers/{providerId}/resources")
  public List<ResourceResponse> resources(@PathVariable String providerId) {
    return findProvider(providerId).listResources().stream().map(ResourceResponse::from).toList();
  }

  @GetMapping("/providers/{providerId}/prompts")
  public List<PromptResponse> prompts(@PathVariable String providerId) {
    return findProvider(providerId).listPrompts().stream().map(PromptResponse::from).toList();
  }

  private LoadedSeaProvider findLoadedProvider(String providerId) {
    return seaRuntime
        .findLoadedProvider(providerId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "SEA provider not found: " + providerId));
  }

  private SeaProvider findProvider(String providerId) {
    return findLoadedProvider(providerId).provider();
  }

  public record ModuleResponse(
      String moduleId, String version, String displayName, String description) {
    static ModuleResponse from(ModuleDescriptor descriptor) {
      return new ModuleResponse(
          descriptor.moduleId(),
          descriptor.version(),
          descriptor.displayName(),
          descriptor.description());
    }
  }

  public record LoadedProviderResponse(
      ModuleResponse module, ProviderFactoryResponse factory, ProviderResponse provider) {
    static LoadedProviderResponse from(LoadedSeaProvider loadedProvider) {
      return new LoadedProviderResponse(
          ModuleResponse.from(loadedProvider.module()),
          ProviderFactoryResponse.from(loadedProvider.factory()),
          ProviderResponse.from(loadedProvider.provider().descriptor()));
    }
  }

  public record ProviderFactoryResponse(
      String factoryId,
      String moduleId,
      String providerType,
      String displayName,
      String description) {
    static ProviderFactoryResponse from(ProviderFactoryDescriptor descriptor) {
      return new ProviderFactoryResponse(
          descriptor.factoryId(),
          descriptor.moduleId(),
          descriptor.providerType(),
          descriptor.displayName(),
          descriptor.description());
    }
  }

  public record ProviderResponse(
      String providerId,
      String moduleId,
      String providerType,
      String displayName,
      String description,
      String version,
      CapabilitiesResponse capabilities,
      List<String> policyTags,
      Map<String, String> scope) {
    static ProviderResponse from(ProviderDescriptor descriptor) {
      return new ProviderResponse(
          descriptor.providerId(),
          descriptor.moduleId(),
          descriptor.providerType(),
          descriptor.displayName(),
          descriptor.description(),
          descriptor.version(),
          CapabilitiesResponse.from(descriptor.capabilities()),
          descriptor.policyTags(),
          descriptor.scope());
    }
  }

  public record CapabilitiesResponse(
      boolean supportsTools,
      boolean supportsResources,
      boolean supportsPrompts,
      boolean supportsResourceTemplates,
      boolean supportsSubscriptions,
      boolean supportsNotifications,
      boolean supportsStructuredToolOutput,
      boolean supportsStreaming) {
    static CapabilitiesResponse from(ProviderCapabilities capabilities) {
      return new CapabilitiesResponse(
          capabilities.supportsTools(),
          capabilities.supportsResources(),
          capabilities.supportsPrompts(),
          capabilities.supportsResourceTemplates(),
          capabilities.supportsSubscriptions(),
          capabilities.supportsNotifications(),
          capabilities.supportsStructuredToolOutput(),
          capabilities.supportsStreaming());
    }
  }

  public record ToolResponse(
      String name,
      String description,
      boolean sideEffecting,
      String classification,
      String routing,
      List<String> policyTags) {
    static ToolResponse from(SeaToolDescriptor descriptor) {
      return new ToolResponse(
          descriptor.name(),
          descriptor.description(),
          descriptor.sideEffecting(),
          "sea_backed",
          "sea_backed",
          descriptor.policyTags());
    }
  }

  public record ResourceResponse(String uri, String description) {
    static ResourceResponse from(ResourceDescriptor descriptor) {
      return new ResourceResponse(descriptor.uri(), descriptor.description());
    }
  }

  public record PromptResponse(String name, String description) {
    static PromptResponse from(PromptDescriptor descriptor) {
      return new PromptResponse(descriptor.name(), descriptor.description());
    }
  }
}
