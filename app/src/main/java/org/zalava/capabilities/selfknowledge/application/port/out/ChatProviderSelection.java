package org.zalava.capabilities.selfknowledge.application.port.out;

/** Selection alone does not establish provider health or usable credentials. */
public interface ChatProviderSelection {
  boolean selected();
}
