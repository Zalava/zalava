package org.zalava.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.zalava.ModuleDescriptor;
import org.zalava.ZalavaModule;

public final class StaticSeaModuleRegistry implements SeaModuleRegistry {

  private final List<ZalavaModule> modules;

  public StaticSeaModuleRegistry(Collection<ZalavaModule> modules) {
    modules.stream()
        .map(ZalavaModule::descriptor)
        .collect(
            Collectors.toMap(
                ModuleDescriptor::moduleId,
                Function.identity(),
                (first, duplicate) -> {
                  throw new IllegalArgumentException(
                      "Duplicate SEA module id: " + first.moduleId());
                }));
    this.modules =
        modules.stream()
            .sorted(Comparator.comparing(module -> module.descriptor().moduleId()))
            .toList();
  }

  @Override
  public List<ZalavaModule> modules() {
    return modules;
  }
}
