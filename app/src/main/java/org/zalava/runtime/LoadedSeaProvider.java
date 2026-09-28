package org.zalava.runtime;

import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaProvider;

public record LoadedSeaProvider(
    ModuleDescriptor module, ProviderFactoryDescriptor factory, SeaProvider provider) {}
