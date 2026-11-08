package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.web.ui.ModulesController.EnabledModuleEntry;
import org.zalava.web.ui.ModulesController.ModuleEntry;
import org.zalava.web.ui.ModulesController.ModulesModel;
import org.zalava.web.ui.ModulesController.RestartEntry;

class ModuleRestartStateTest {
  private static final RestartEntry RESTART = new RestartEntry("AVAILABLE", "IDLE", "Ready");

  @Test
  void identifiesInstallsUpgradesAndRemovalsWithoutFlaggingBuiltins() {
    var builtin = loaded("builtin", "1", false);
    var external = loaded("external", "1", true);
    assertThat(model(List.of(builtin), List.of()).moduleRestartRequired()).isFalse();
    assertThat(model(List.of(builtin), List.of(enabled("external", "1"))).moduleRestartRequired())
        .isTrue();
    assertThat(model(List.of(external), List.of(enabled("external", "1"))).moduleRestartRequired())
        .isFalse();
    assertThat(model(List.of(external), List.of(enabled("external", "2"))).moduleRestartRequired())
        .isTrue();
    assertThat(model(List.of(external), List.of()).moduleRestartRequired()).isTrue();
  }

  private static ModuleEntry loaded(String id, String version, boolean external) {
    return new ModuleEntry(id, id, version, "", 0, "", "", "", "", "", external);
  }

  private static EnabledModuleEntry enabled(String id, String version) {
    return new EnabledModuleEntry(id, version, "", "", List.of());
  }

  private static ModulesModel model(List<ModuleEntry> loaded, List<EnabledModuleEntry> enabled) {
    return new ModulesModel(loaded, enabled, List.of(), null, List.of(), RESTART);
  }
}
