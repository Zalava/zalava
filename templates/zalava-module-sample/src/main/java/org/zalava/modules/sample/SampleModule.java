package org.zalava.modules.sample;

import java.util.List;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;

public final class SampleModule implements SeaModule {
  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor("zalava-module-sample", "0.1.0-alpha.1", "Sample", "External sample");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of();
  }
}
