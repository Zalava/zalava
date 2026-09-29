package org.zalava.web;

public interface ZalavaWebExtension {

  WebExtensionDescriptor descriptor();

  void register(WebExtensionRegistry registry);
}
