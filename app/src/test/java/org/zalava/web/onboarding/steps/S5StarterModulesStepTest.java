package org.zalava.web.onboarding.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.runtime.ZalavaRuntime;

class S5StarterModulesStepTest {

  @TempDir Path configurationRoot;

  private FileSystemModuleConfigurationStore store;
  private ZalavaRuntime runtime;

  @BeforeEach
  void setUp() {
    store = new FileSystemModuleConfigurationStore(configurationRoot);
  }

  @Test
  void exposesOptionalStarterStepIdentity() {
    S5StarterModulesStep step = step(List.of());

    assertThat(step.getStepId()).isEqualTo("starters");
    assertThat(step.getStepTitle()).isEqualTo("Starter modules");
    assertThat(step.getTemplatePath()).isEqualTo("onboarding/steps/S5-starters");
    assertThat(step.isOptional()).isTrue();
  }

  @Test
  void prepareModelListsCuratedModulesWithConfigurationStatus() {
    runtime = runtimeWith(catalogModule("time", "Time module", schemaWithProperties()));
    S5StarterModulesStep step = step(List.of("time", "absent-module"));

    Map<String, Object> model = new HashMap<>();
    step.prepareModel(new HashMap<>(), model);

    assertThat(model.get("starterModules"))
        .asInstanceOf(
            org.assertj.core.api.InstanceOfAssertFactories.list(
                S5StarterModulesStep.StarterModule.class))
        .hasSize(1)
        .first()
        .satisfies(
            starter -> {
              assertThat(starter.moduleId()).isEqualTo("time");
              assertThat(starter.displayName()).isEqualTo("Time module");
              assertThat(starter.configured()).isFalse();
            });
    assertThat(model.get("selectedStarterModuleId")).isNull();
    assertThat(model.get("selectedStarterConfigured")).isEqualTo(false);
  }

  @Test
  void prepareModelMarksTheSelectedModuleConfiguredOnceActivated() {
    runtime = runtimeWith(catalogModule("time", "Time module", schemaWithProperties()));
    store.saveCandidate(
        new ModuleConfigurationSnapshot("time", "1.0.0", "schema", Map.of(), Map.of()), Map.of());
    store.promoteCandidate("time");
    S5StarterModulesStep step = step(List.of("time"));

    Map<String, Object> session = new HashMap<>();
    session.put("onboarding.starter.module-id", "time");
    Map<String, Object> model = new HashMap<>();
    step.prepareModel(session, model);

    assertThat(model.get("selectedStarterModuleId")).isEqualTo("time");
    assertThat(model.get("selectedStarterConfigured")).isEqualTo(true);
  }

  @Test
  void selectRequiresAnAvailableCuratedModule() {
    runtime = runtimeWith(catalogModule("time", "Time module", schemaWithProperties()));
    S5StarterModulesStep step = step(List.of("time"));

    Map<String, Object> session = new HashMap<>();
    assertThat(step.processStep(Map.of("action", "select", "moduleId", "unknown"), session))
        .isEqualTo("This starter module is not available in this Zalava installation.");

    assertThat(step.processStep(Map.of("action", "select", "moduleId", "time"), session)).isEmpty();
    assertThat(session.get("onboarding.starter.module-id")).isEqualTo("time");
  }

  @Test
  void deselectClearsTheCurrentSelection() {
    S5StarterModulesStep step = step(List.of());
    Map<String, Object> session = new HashMap<>();
    session.put("onboarding.starter.module-id", "time");

    assertThat(step.processStep(Map.of("action", "deselect"), session)).isEmpty();

    assertThat(session).doesNotContainKey("onboarding.starter.module-id");
  }

  @Test
  void continueIsBlockedWhileTheSelectedModuleStillNeedsConfiguration() {
    runtime = runtimeWith(catalogModule("time", "Time module", schemaWithProperties()));
    S5StarterModulesStep step = step(List.of("time"));
    Map<String, Object> session = new HashMap<>();
    session.put("onboarding.starter.module-id", "time");

    assertThat(step.processStep(Map.of(), session))
        .isEqualTo("Configure the selected starter module or deselect it before continuing.");

    store.saveCandidate(
        new ModuleConfigurationSnapshot("time", "1.0.0", "schema", Map.of(), Map.of()), Map.of());
    store.promoteCandidate("time");
    assertThat(step.processStep(Map.of(), session)).isNull();
  }

  @Test
  void selectionUpdateIsDetectedFromAnEmptyNextStepId() {
    var submission =
        new org.zalava.web.onboarding.domain.OnboardingSubmission("starters", "", null);

    assertThat(S5StarterModulesStep.isSelectionUpdate(submission)).isTrue();
    assertThat(
            S5StarterModulesStep.isSelectionUpdate(
                new org.zalava.web.onboarding.domain.OnboardingSubmission(
                    "starters", "complete", null)))
        .isFalse();
  }

  @Test
  void modulesWithoutConfigurationPropertiesAreNotOffered() {
    runtime = runtimeWith(catalogModule("bare", "Bare module", Map.of("type", "object")));
    S5StarterModulesStep step = step(List.of("bare"));

    Map<String, Object> model = new HashMap<>();
    step.prepareModel(new HashMap<>(), model);

    assertThat(model.get("starterModules"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Object.class))
        .isEmpty();
  }

  private S5StarterModulesStep step(List<String> curatedIds) {
    return new S5StarterModulesStep(runtime, store, curatedIds);
  }

  private static ZalavaModule catalogModule(
      String id, String displayName, Map<String, Object> schema) {
    return new ZalavaModule() {
      @Override
      public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(id, "1.0.0", displayName, displayName);
      }

      @Override
      public List<ProviderFactory> providerFactories() {
        return List.of();
      }

      @Override
      public ModuleConfigurationDescriptor configuration() {
        return new ModuleConfigurationDescriptor(schema);
      }
    };
  }

  private static Map<String, Object> schemaWithProperties() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("city", Map.of("type", "string", "description", "City to report")));
  }

  private static ZalavaRuntime runtimeWith(ZalavaModule... modules) {
    return new ZalavaRuntime() {
      @Override
      public List<ZalavaModule> modules() {
        return List.of(modules);
      }

      @Override
      public List<org.zalava.modules.runtime.LoadedZalavaProvider> loadedProviders() {
        return List.of();
      }
    };
  }
}
