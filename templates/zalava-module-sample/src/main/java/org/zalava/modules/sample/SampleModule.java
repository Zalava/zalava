package org.zalava.modules.sample;

import java.util.List;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;

public final class SampleModule implements ZalavaModule {
  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        "zalava-module-sample", "0.1.0-alpha.1", "Sample", "External sample");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of();
  }
}
