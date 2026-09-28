package org.zalava.web;

public interface SeaWebExtension {

  WebExtensionDescriptor descriptor();

  void register(WebExtensionRegistry registry);
}
