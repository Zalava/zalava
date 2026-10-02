package org.zalava.modules.runtime;

import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaProvider;

public record LoadedSeaProvider(
    ModuleDescriptor module, ProviderFactoryDescriptor factory, ZalavaProvider provider) {}
