package org.zalava.catalog;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import org.zalava.FactorySecretAccess;
import org.zalava.ModuleConfigurationDescriptor;
import org.zalava.ModuleConfigurationStatus;
import org.zalava.ProviderFactoryContext;
import tools.jackson.databind.ObjectMapper;

/** Application boundary for module-owned configuration documents. */
public final class ModuleConfigurationValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SchemaRegistry SCHEMAS =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  public ModuleConfigurationStatus validate(
      SourceModuleIndex.Module catalog,
      ModuleConfigurationDescriptor packaged,
      Map<String, Object> document) {
    requireAgreement(catalog, packaged);
    validateDocument(packaged, document);
    return document.isEmpty()
        ? ModuleConfigurationStatus.SETUP_REQUIRED
        : ModuleConfigurationStatus.RESTART_REQUIRED;
  }

  /** Validates a product-form submission against the module's packaged schema. */
  public void validateDocument(
      ModuleConfigurationDescriptor descriptor, Map<String, Object> document) {
    var violations =
        SCHEMAS
            .getSchema(JSON.valueToTree(descriptor.jsonSchema()))
            .validate(JSON.valueToTree(document));
    if (!violations.isEmpty()) {
      var violation =
          violations.stream()
              .min(Comparator.comparing(v -> v.getInstanceLocation().toString()))
              .orElseThrow();
      throw new IllegalArgumentException(
          "Module configuration violates schema at "
              + (violation.getInstanceLocation().toString().isBlank()
                  ? "$"
                  : violation.getInstanceLocation())
              + ": "
              + violation.getMessage());
    }
  }

  public ProviderFactoryContext factoryContext(Map<String, Object> document, String factoryId) {
    return factoryContext(document, factoryId, FactorySecretAccess.none());
  }

  public ProviderFactoryContext factoryContext(
      Map<String, Object> document, String factoryId, FactorySecretAccess secrets) {
    return new ProviderFactoryContext(
            Map.of("modules", Map.of("module", Map.of("factories", document))), secrets)
        .forFactory("module", factoryId);
  }

  private static void requireAgreement(
      SourceModuleIndex.Module catalog, ModuleConfigurationDescriptor packaged) {
    if (!catalog.configurationSchema().equals(packaged.jsonSchema())) {
      throw new IllegalArgumentException(
          "Catalog and packaged module configuration schemas differ");
    }
    Set<String> catalogFactories =
        catalog.factories().stream()
            .map(SourceModuleIndex.Factory::factoryId)
            .collect(java.util.stream.Collectors.toSet());
    Object properties = packaged.jsonSchema().get("properties");
    if (!(properties instanceof Map<?, ?> map) || !map.keySet().equals(catalogFactories)) {
      throw new IllegalArgumentException(
          "Catalog and packaged module factory configuration differ");
    }
  }
}
