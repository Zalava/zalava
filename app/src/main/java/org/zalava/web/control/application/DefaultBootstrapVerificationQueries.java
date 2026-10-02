package org.zalava.web.control.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaVerificationDescriptor;
import org.zalava.api.ZalavaVerificationStep;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries;
import org.zalava.web.control.application.port.out.RuntimeVerificationCatalog;

public final class DefaultBootstrapVerificationQueries implements BootstrapVerificationQueries {
  private final RuntimeVerificationCatalog catalog;

  public DefaultBootstrapVerificationQueries(RuntimeVerificationCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public List<BootstrapToolVerification> bootstrapVerification() {
    return catalog.verifications().stream()
        .map(this::verification)
        .sorted(
            Comparator.comparing(BootstrapToolVerification::toolset)
                .thenComparing(BootstrapToolVerification::providerId))
        .toList();
  }

  private BootstrapToolVerification verification(ZalavaVerificationDescriptor descriptor) {
    Optional<ZalavaProvider> provider = catalog.findProvider(descriptor.providerId());
    if (provider.isEmpty())
      return new BootstrapToolVerification(
          descriptor.toolset(),
          descriptor.providerId(),
          null,
          false,
          "Provider is not loaded.",
          descriptor.steps().stream().map(DefaultBootstrapVerificationQueries::step).toList());
    List<String> missing =
        descriptor.requiredTools().stream()
            .filter(
                tool ->
                    provider.get().listTools().stream()
                        .noneMatch(available -> available.name().equals(tool)))
            .toList();
    return new BootstrapToolVerification(
        descriptor.toolset(),
        descriptor.providerId(),
        provider.get().descriptor().displayName(),
        missing.isEmpty(),
        missing.isEmpty() ? null : "Missing tools: " + String.join(", ", missing),
        descriptor.steps().stream().map(DefaultBootstrapVerificationQueries::step).toList());
  }

  private static VerificationStep step(ZalavaVerificationStep step) {
    return new VerificationStep(
        step.label(),
        step.method(),
        step.path(),
        step.toolName(),
        step.sideEffecting(),
        step.confirmationRequired(),
        step.body());
  }
}
