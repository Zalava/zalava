package org.zalava.capabilities.discovery.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.discovery.application.port.out.ModuleCapabilityCatalog;

public final class DefaultModuleSuggestions implements ModuleSuggestions {
  private static final Set<String> GENERIC =
      Set.of(
          "the", "and", "for", "with", "that", "this", "can", "you", "please", "module", "zalava");
  private final ModuleCapabilityCatalog catalog;

  public DefaultModuleSuggestions(ModuleCapabilityCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public Outcome suggest(String capability) {
    if (capability == null || capability.isBlank() || capability.length() > 2000)
      throw new IllegalArgumentException("Capability must contain between 1 and 2000 characters");
    List<String> terms =
        Arrays.stream(normalize(capability).split(" +"))
            .filter(term -> term.length() > 2 && !GENERIC.contains(term))
            .distinct()
            .limit(30)
            .toList();
    try {
      List<Module> matches =
          catalog.modules().stream()
              .limit(100)
              .map(DefaultModuleSuggestions::bounded)
              .map(module -> new Scored(module, score(module, terms)))
              .filter(value -> value.score() > 0)
              .sorted(
                  Comparator.comparingLong(Scored::score)
                      .reversed()
                      .thenComparing(value -> value.module().moduleId()))
              .map(Scored::module)
              .distinct()
              .limit(5)
              .toList();
      return new Outcome(matches.isEmpty() ? Status.NO_MATCH : Status.MATCH, matches);
    } catch (RuntimeException unavailable) {
      return Outcome.of(Status.UNAVAILABLE);
    }
  }

  private static Module bounded(Module module) {
    if (module == null
        || module.moduleId() == null
        || !module.moduleId().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}"))
      throw new IllegalArgumentException("Invalid catalog module metadata");
    return new Module(
        module.moduleId(), text(module.displayName(), 160), text(module.description(), 1500));
  }

  private static String text(String value, int limit) {
    if (value == null) return "";
    String clean = value.replaceAll("[\\p{Cntrl}]+", " ").strip();
    return clean.length() > limit ? clean.substring(0, limit) : clean;
  }

  private static long score(Module module, List<String> terms) {
    String text =
        normalize(module.moduleId() + " " + module.displayName() + " " + module.description());
    // Partial token overlap is deliberately a possible metadata match, never proof of capability.
    return terms.stream().filter(text::contains).count();
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
  }

  private record Scored(Module module, long score) {}
}
