package org.zalava.modules.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ZalavaModule;

public final class StaticZalavaModuleRegistry implements ZalavaModuleRegistry {

  private final List<ZalavaModule> modules;

  public StaticZalavaModuleRegistry(Collection<ZalavaModule> modules) {
    modules.stream()
        .map(ZalavaModule::descriptor)
        .collect(
            Collectors.toMap(
                ModuleDescriptor::moduleId,
                Function.identity(),
                (first, duplicate) -> {
                  throw new IllegalArgumentException(
                      "Duplicate Zalava module id: " + first.moduleId());
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
