package org.zalava.modules.runtime;

import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

public record LoadedZalavaProvider(
    ModuleDescriptor module, ProviderFactoryDescriptor factory, ZalavaProvider provider) {}
