package org.zalava.api.extensions.web;

public interface ZalavaWebExtension {

  WebExtensionDescriptor descriptor();

  void register(WebExtensionRegistry registry);
}
